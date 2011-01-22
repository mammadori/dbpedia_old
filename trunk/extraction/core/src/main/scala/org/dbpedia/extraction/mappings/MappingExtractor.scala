package org.dbpedia.extraction.mappings

import java.util.logging.Logger
import org.dbpedia.extraction.destinations.Graph
import org.dbpedia.extraction.wikiparser._

/**
 *  Extracts structured data based on hand-generated mappings of Wikipedia infoboxes to the DBpedia ontology.
 */
class MappingExtractor(context : ExtractionContext) extends Extractor
{
    private val logger = Logger.getLogger(classOf[MappingExtractor].getName)

    private val mappings = MappingsLoader.load(context)

    private val templateMappings = mappings._1

    private val tableMappings = mappings._2

    private val conditionalMappings = mappings._3
    
    private val resolvedMappings = context.redirects.resolveMap(templateMappings) ++ context.redirects.resolveMap(conditionalMappings)

    override def extract(page : PageNode, subjectUri : String, pageContext : PageContext) : Graph =
    {
        if(page.title.namespace != WikiTitle.Namespace.Main) return new Graph()

        extractNode(page, subjectUri, pageContext)
    }

    /**
     * Extracts a data from a node.
     * Recursively traverses it children if the node itself does not contain any useful data.
     */
    private def extractNode(node : Node, subjectUri : String, pageContext : PageContext) : Graph =
    {
        //Try to extract data from the node itself
        val graph = node match
        {
            case templateNode : TemplateNode =>
            {
                resolvedMappings.get(templateNode.title.decoded) match
                {
                    case Some(mapping) => mapping.extract(templateNode, subjectUri, pageContext)
                    case None => new Graph()
                }
            }
            case tableNode : TableNode =>
            {
                tableMappings.map(_.extract(tableNode, subjectUri, pageContext))
                             .foldLeft(new Graph())(_ merge _)
            }
            case _ => new Graph()
        }

        //Check the result and return it if non-empty.
        //Otherwise continue with extracting the children of the current node.
        if(graph.isEmpty)
        {
            node.children.map(child => extractNode(child, subjectUri, pageContext))
                         .foldLeft(new Graph())(_ merge _)
        }
        else
        {
            graph
        }
    }
}
