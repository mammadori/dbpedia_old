package org.dbpedia.extraction.mappings

import org.dbpedia.extraction.ontology.OntologyNamespaces
import org.dbpedia.extraction.destinations.{Graph, DBpediaDatasets, Quad}
import org.dbpedia.extraction.wikiparser.{PageNode, WikiTitle, InternalLinkNode, Node}

/**
 * Extracts internal links between DBpedia instances from the internal pagelinks between Wikipedia articles.
 * The page links might be useful for structural analysis, data mining or for ranking DBpedia instances using Page Rank or similar algorithms.
 */
class PageLinksExtractor(extractionContext : ExtractionContext) extends Extractor
{
    val wikiPageWikiLinkProperty = extractionContext.ontology.getProperty("wikiPageWikiLink")
                                   .getOrElse(throw new NoSuchElementException("Ontology property 'wikiPageWikiLink' does not exist in DBpedia Ontology."))

    override def extract(node : PageNode, subjectUri : String, pageContext : PageContext) : Graph =
    {
        if(node.title.namespace != WikiTitle.Namespace.Main) return new Graph()
        
        var quads = List[Quad]()
        val list = collectInternalLinks(node)
        list.foreach(link => {
            quads ::= new Quad(extractionContext, DBpediaDatasets.PageLinks, subjectUri, wikiPageWikiLinkProperty,
                getUri(link.destination), link.sourceUri, null)
        })
        new Graph(quads)
    }

    private def collectInternalLinks(node : Node) : List[InternalLinkNode] =
    {
        node match
        {
            case linkNode : InternalLinkNode => List(linkNode)
            case _ => node.children.flatMap(collectInternalLinks)
        }
    }

    private def getUri(destination : WikiTitle) : String =
    {
        OntologyNamespaces.getUri(destination.encodedWithNamespace, OntologyNamespaces.DBPEDIA_INSTANCE_NAMESPACE)
    }
}