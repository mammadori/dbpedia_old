package org.dbpedia.lookup.lucene

import java.io.File
import org.apache.lucene.store.FSDirectory
import org.apache.lucene.search._
import org.apache.lucene.index.{Term, IndexReader}
import org.dbpedia.lookup.util.WikiUtil
import org.dbpedia.lookup.server.Result
import org.apache.lucene.queryParser.QueryParser

/**
 * Created by IntelliJ IDEA.
 * User: Max Jakob
 * Date: 14.01.11
 * Time: 14:43
 * Class to query the Lucene index for the best URI given a surface form.
 */

class Searcher(val indexDir: File = LuceneConfig.defaultIndex) {

    private val indexReader = IndexReader.open(FSDirectory.open(indexDir), true)  // read-only
    private val indexSearcher = new IndexSearcher(indexReader)
    private val sort = new Sort(new SortField(LuceneConfig.Fields.REFCOUNT, SortField.INT, true))


    def exactSearch(keyword: String, ontologyClass: String="", maxResults: Int=5): List[Result] = {
        if(keyword == null || keyword.isEmpty) {
            return List.empty
        }
        val query = getQuery(keyword, ontologyClass, prefixQuery = false)
        search(query, maxResults)
    }

    def prefixSearch(keyword: String, ontologyClass: String="", maxResults: Int=5): List[Result] = {
        if(keyword == null || keyword.isEmpty) {
            return List.empty
        }
        val query = getQuery(keyword, ontologyClass, prefixQuery = true)
        search(query, maxResults)
    }

    def close() {
        indexSearcher.close
        indexReader.close
    }


    private def search(query: Query, maxResults: Int): List[Result] = {
        indexSearcher.search(query, null, maxResults, sort).scoreDocs.toList.map(getResult)
    }

    private def getQuery(keyword: String, ontologyClass: String, prefixQuery: Boolean = false): Query = {
        val searchKeyword = QueryParser.escape(WikiUtil.wikiDecode(keyword)).toLowerCase  //toLowerCase

        val bq = new BooleanQuery()

        if(prefixQuery) {
            bq.add(new PrefixQuery(new Term(LuceneConfig.Fields.SURFACE_FORM, searchKeyword)), BooleanClause.Occur.MUST)
        }
        else {
            bq.add(new TermQuery(new Term(LuceneConfig.Fields.SURFACE_FORM, searchKeyword)), BooleanClause.Occur.MUST)
        }

        if(ontologyClass != null && ontologyClass.trim != "") {
            //is full class URI
            if(ontologyClass startsWith "http://dbpedia.org/ontology/") {
                bq.add(new TermQuery(new Term(LuceneConfig.Fields.CLASS, ontologyClass.trim)), BooleanClause.Occur.MUST)
            }
            //abbreviated namespace prefix
            else if(ontologyClass.startsWith("dbpedia:") || ontologyClass.startsWith("dbpedia-owl:")) {
                val c = ontologyClass.trim.replace("dbpedia:", "").replace("dbpedia-owl:", "")
                bq.add(new TermQuery(new Term(LuceneConfig.Fields.CLASS, "http://dbpedia.org/ontology/"+c)), BooleanClause.Occur.MUST)
            }
            //label given: make camel case and attach namespace
            else {
                val camel = ontologyClass.trim.split(" ").map(_.capitalize).mkString("")
                bq.add(new TermQuery(new Term(LuceneConfig.Fields.CLASS, "http://dbpedia.org/ontology/"+camel)), BooleanClause.Occur.MUST)
            }
        }

        bq
    }

    private def getResult(scoreDoc: ScoreDoc): Result = {
        val doc = indexReader.document(scoreDoc.doc)

        val uri: String = doc.get(LuceneConfig.Fields.URI)
        val description: String = doc.get(LuceneConfig.Fields.DESCRIPTION)
        val ontologyClasses: List[String] = doc.getValues(LuceneConfig.Fields.CLASS) match {
            case null => List.empty
            case classes => classes.toList
        }
        val categories: Set[String] = doc.getValues(LuceneConfig.Fields.CATEGORY) match {
            case null => Set.empty
            case cats => cats.toSet
        }
        val templates: Set[String] = doc.getValues(LuceneConfig.Fields.TEMPLATE) match {
            case null => Set.empty
            case temps => temps.toSet
        }
        val redirects: Set[String] = doc.getValues(LuceneConfig.Fields.REDIRECT) match {
            case null => Set.empty
            case reds => reds.toSet
        }
        val refCount: Int = doc.get(LuceneConfig.Fields.REFCOUNT) match {
            case null => 0
            case count: String => count.toInt
        }

        new Result(uri, description, ontologyClasses, categories, templates, redirects, refCount)
    }

}