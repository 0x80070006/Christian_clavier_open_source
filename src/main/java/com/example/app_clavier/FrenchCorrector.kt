package com.example.app_clavier

import java.io.Reader
import java.text.Normalizer
import java.util.Locale
import kotlin.math.ln
import kotlin.math.min

/** Local trie search with bounded Damerau-Levenshtein distance and corpus frequency ranking. */
class FrenchCorrector(reader: Reader) {
    data class Candidate(val word: String, val distance: Int, val frequency: Long, val score: Double, val sparse: Boolean = false)
    private class Node {
        val children=HashMap<Char,Node>()
        val words=ArrayList<Pair<String,Long>>(1)
    }
    private val root=Node()
    private val exact=HashSet<String>()
    private val sparsePrefixes=HashMap<String,MutableList<Pair<String,Long>>>()
    private fun canonical(word:String)=word.lowercase(Locale.FRENCH).replace('’','\'')
    private fun presentation(word:String,template:String)=if(template.contains('’'))word.replace('\'','’') else word
    private fun skeleton(word:String)=fold(word).filter{it !in "aeiouy"}
    private fun subsequence(query:String,word:String):Boolean {
        var i=0;for(ch in word){if(i<query.length && query[i]==ch)i++};return i==query.length
    }
    val wordCount: Int get()=exact.size
    init {
        reader.buffered().useLines { lines -> lines.forEach { line ->
            val parts=line.trim().split(' ')
            val word=canonical(parts.firstOrNull() ?: "")
            val frequency=parts.lastOrNull()?.toLongOrNull() ?: 0L
            if(word.length in 2..30 && word.all { it.isLetter() || it=='\'' || it=='’' || it=='-' }) {
                exact.add(word)
                var node=root
                for(ch in fold(word)) node=node.children.getOrPut(ch){Node()}
                node.words.add(word to frequency)
                val compact=skeleton(word)
                if(compact.length>=3)for(size in 3..minOf(4,compact.length))sparsePrefixes.getOrPut(compact.take(size)){ArrayList()}.add(word to frequency)
            }
        } }
    }
    fun contains(word:String)=exact.contains(canonical(word))
    fun candidates(word:String, tolerance:Int):List<Candidate> {
        if(word.length !in 2..30 || word.any { !it.isLetter() && it!='\'' && it!='’' && it!='-' })return emptyList()
        val normalized=canonical(word)
        val q=fold(normalized)
        val limit=if(tolerance<40 || q.length<5)1 else 2
        val found=ArrayList<Candidate>()
        val initial=IntArray(q.length+1){it}
        fun walk(ch:Char,node:Node,prev:IntArray,older:IntArray?,previousChar:Char?) {
            val row=IntArray(q.length+1);row[0]=prev[0]+1
            var minimum=row[0]
            for(j in 1..q.length) {
                var d=min(min(row[j-1]+1,prev[j]+1),prev[j-1]+if(ch==q[j-1])0 else 1)
                if(older!=null && j>1 && ch==q[j-2] && previousChar==q[j-1])d=min(d,older[j-2]+1)
                row[j]=d;minimum=min(minimum,d)
            }
            if(row[q.length]<=limit)node.words.forEach { (w,f) ->
                val d=row[q.length]
                found.add(Candidate(presentation(w,word),d,f,d*3.2-ln(f+1.0)*.18 + if(w==normalized) -4.0 else 0.0))
            }
            if(minimum<=limit)node.children.forEach{(next,child)->walk(next,child,row,prev,ch)}
        }
        root.children.forEach{(ch,node)->walk(ch,node,initial,null,null)}
        // Missing-letter matching remains deliberately narrow: same consonant beginning,
        // typed letters in order, and at most four omitted characters.
        if(tolerance>=55 && skeleton(normalized).length>=3){
            val key=skeleton(normalized).take(4)
            sparsePrefixes[key].orEmpty().forEach{(candidate,frequency)->
                val gap=candidate.length-normalized.length
                if(gap in 2..4 && subsequence(q,fold(candidate)) && found.none{it.word==presentation(candidate,word)}){
                    found.add(Candidate(presentation(candidate,word),gap,frequency,gap*1.05-ln(frequency+1.0)*.18,true))
                }
            }
        }
        return found.sortedWith(compareBy<Candidate>{it.score}.thenByDescending{it.frequency}).take(3)
    }
    fun correction(word:String,tolerance:Int):String? {
        if(tolerance==0 || word.length<3 || contains(word) || word.all{it.isUpperCase()} || word.first().isUpperCase())return null
        val choices=candidates(word,tolerance)
        val best=choices.firstOrNull() ?: return null
        val distanceLimit=if(best.sparse)4 else if(tolerance>=70 && word.length>=6)2 else 1
        if(best.distance>distanceLimit)return null
        val minimumFrequency=when {best.sparse->10_000L;tolerance<35->10000L;tolerance<70->1500L;else->200L}
        if(best.frequency<minimumFrequency)return null
        val alternative=choices.getOrNull(1)
        val ratio=if(alternative!=null && alternative.distance==best.distance)best.frequency.toDouble()/(alternative.frequency+1) else 100.0
        val requiredRatio=when {tolerance<35->4.0;tolerance<70->1.5;else->1.05}
        return if(ratio>=requiredRatio)presentation(best.word,word) else null
    }
    companion object {
        fun fold(s:String):String=Normalizer.normalize(s.lowercase(Locale.FRENCH).replace('’','\'').replace("œ","oe"),Normalizer.Form.NFD).replace(Regex("\\p{M}+"),"")
    }
}
