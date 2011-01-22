package org.dbpedia.extraction.mappings

import org.dbpedia.extraction.destinations.{Graph, DBpediaDatasets, Quad}
import org.dbpedia.extraction.ontology.OntologyNamespaces
import org.dbpedia.extraction.wikiparser.PageNode

/**
 * Extracts page ids to articles.
 */
class PageIdExtractor(extractionContext : ExtractionContext) extends Extractor
{
    val wikiPageIdProperty = extractionContext.ontology.getProperty("wikiPageID")
                             .getOrElse(throw new NoSuchElementException("Ontology property 'wikiPageID' does not exist in DBpedia Ontology."))


    override def extract(node : PageNode, subjectUri : String, pageContext : PageContext) : Graph =
    {
        val objectLink = "http://" + extractionContext.language.wikiCode + ".wikipedia.org/wiki/" + node.root.title.encoded

        new Graph(new Quad(extractionContext, DBpediaDatasets.PageIds, objectLink, wikiPageIdProperty,
            node.id.toString, node.sourceUri, extractionContext.ontology.getDatatype("xsd:integer").get ))
    }
}