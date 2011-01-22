package org.dbpedia.extraction.mappings

import org.dbpedia.extraction.util.UriUtils
import org.dbpedia.extraction.ontology.OntologyNamespaces
import collection.mutable.HashSet
import org.dbpedia.extraction.ontology.datatypes.{Datatype, DimensionDatatype}
import org.dbpedia.extraction.wikiparser._
import org.dbpedia.extraction.dataparser._
import org.dbpedia.extraction.util.StringUtils._
import java.net.URLEncoder
import org.dbpedia.extraction.destinations.{DBpediaDatasets, Graph, Quad}
import org.dbpedia.extraction.wikiparser.impl.wikipedia.Namespaces

/**
 * This extractor extracts all properties from all infoboxes.
 * Extracted information is represented using properties in the http://dbpedia.org/property/ namespace.
 * The names of the these properties directly reflect the name of the Wikipedia infobox property.
 * Property names are not cleaned or merged.
 * Property types are not part of a subsumption hierarchy and there is no consistent ontology for the infobox dataset.
 * The infobox extractor performs only a minimal amount of property value clean-up, e.g., by converting a value like “June 2009” to the XML Schema format “2009–06”.
 * You should therefore use the infobox dataset only if your application requires complete coverage of all Wikipeda properties and you are prepared to accept relatively noisy data.
 */
class InfoboxExtractor(extractionContext : ExtractionContext) extends Extractor
{
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Configuration
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private val usesTemplateProperty = OntologyNamespaces.DBPEDIA_GENERAL_NAMESPACE + "wikiPageUsesTemplate"

    private val templateNamespace = Namespaces.getNameForNamespace(extractionContext.language, WikiTitle.Namespace.Template)

    private val MinPropertyCount = 2

    private val MinPercentageOfExplicitPropertyKeys = 0.75

    private val ignoreTemplates = Set("redirect", "seealso", "see_also", "main", "cquote", "chess diagram", "ipa", "lang")

    private val ignoreTemplatesRegex = List("cite.*".r, "citation.*".r, "assessment.*".r, "zh-.*".r, "llang.*".r, "IPA-.*".r)

    private val ignoreProperties = Set("image", "image_photo")

    private val labelProperty = extractionContext.ontology.getProperty("rdfs:label").get
    private val typeProperty = extractionContext.ontology.getProperty("rdf:type").get
    private val propertyClass = extractionContext.ontology.getClass("rdf:Property").get

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Regexes
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private val RankRegex = """(?i)([0-9]+)\s?(?:st|nd|rd|th)""".r

    private val SplitWordsRegex = """_+|\s+|\-|:+""".r

    private val LeadingNumberRegex = """^[0-9]+.*""".r

    private val TrailingNumberRegex = """[0-9]+$""".r

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Parsers
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private val unitValueParsers = extractionContext.ontology.datatypes
                                   .filter(_.isInstanceOf[DimensionDatatype]).map(_.asInstanceOf[DimensionDatatype])
                                   .map(dimension => new UnitValueParser(extractionContext, dimension, true))

    private val intParser = new IntegerParser(extractionContext, true)

    private val doubleParser = new DoubleParser(extractionContext, true)

    private val dataTimeParsers = List("xsd:date", "xsd:gMonthYear", "xsd:gMonthDay", "xsd:gMonth" /*, "xsd:gYear", "xsd:gDay"*/)
                                  .map(datatype => new DateTimeParser(extractionContext, new Datatype(datatype), true))

    private val objectParser = new ObjectParser(true)

    private val linkParser = new LinkParser(true)

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // State
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private val seenProperties = HashSet[String]()
    
    override def extract(node : PageNode, subjectUri : String, pageContext : PageContext) : Graph =
    {
        if(node.title.namespace != WikiTitle.Namespace.Main) return new Graph()
        
        var quads = List[Quad]()

        val seenTemplates = new HashSet[String]()

        /** Retrieve all templates on the page which are not ignored */
        val templateList = for(template <- collectTemplates(node);
                               resolvedTitle = extractionContext.redirects.resolve(template.title).decoded.toLowerCase;
                               if !ignoreTemplates.contains(resolvedTitle);
                               if !ignoreTemplatesRegex.exists(regex => regex.unapplySeq(resolvedTitle).isDefined) )
                               yield template

        templateList.foreach(template => {
            val propertyList = template.children.filterNot(property => ignoreProperties.contains(property.key.toLowerCase))

            var propertiesFound = false

            // check how many property keys are explicitly defined
            val countExplicitPropertyKeys = propertyList.count(property => !property.key.forall(_.isDigit))
            if ((countExplicitPropertyKeys >= MinPropertyCount) && (countExplicitPropertyKeys.toDouble / propertyList.size) > MinPercentageOfExplicitPropertyKeys)
            {
                for(property <- propertyList; if (!property.key.forall(_.isDigit))) {
                    // TODO clean HTML

                    val cleanedPropertyNode = NodeUtil.removeParentheses(property)

                    val splitPropertyNodes = NodeUtil.splitPropertyNode(cleanedPropertyNode, """<br\s*\/?>""")
                    for(splitNode <- splitPropertyNodes; (value, datatype) <- extractValue(splitNode))
                    {
                        val propertyUri = getPropertyUri(property.key)
                        try
                        {
                            quads ::= new Quad(extractionContext, DBpediaDatasets.Infoboxes, subjectUri, propertyUri, value, splitNode.sourceUri, datatype)
                        }
                        catch
                        {
                            case ex : IllegalArgumentException => println(ex)
                        }
                        propertiesFound = true
                        seenProperties.synchronized
                        {
                            if (!seenProperties.contains(propertyUri))
                            {
                                val propertyLabel = getPropertyLabel(property.key)
                                seenProperties += propertyUri
                                quads ::= new Quad(extractionContext, DBpediaDatasets.InfoboxProperties, propertyUri, typeProperty, propertyClass.uri, splitNode.sourceUri)
                                quads ::= new Quad(extractionContext, DBpediaDatasets.InfoboxProperties, propertyUri, labelProperty, propertyLabel, splitNode.sourceUri, new Datatype("xsd:string"))
                            }
                        }
                    }

                }

                // TODO write only wikiPageUsesTemplate if properties extracted
                if (propertiesFound && (!seenTemplates.contains(template.title.encoded)))
                {
                    quads ::= new Quad(extractionContext, DBpediaDatasets.Infoboxes, subjectUri, usesTemplateProperty, "http://dbpedia.org/resource/" + templateNamespace + ":" + template.title.encoded, template.sourceUri, null)
                    seenTemplates.add(template.title.encoded)
                }
            }
        })

        new Graph(quads)
    }

    private def extractValue(node : PropertyNode) : List[(String, Datatype)] =
    {
        // TODO don't convert to SI units (what happens to {{convert|25|kg}} ?)
        extractUnitValue(node).foreach(result => return List(result))
        extractNumber(node).foreach(result =>  return List(result))
        extractRankNumber(node).foreach(result => return List(result))
        extractDates(node) match
        {
            case dates if !dates.isEmpty => return dates
            case _ => 
        }
        extractLinks(node) match
        {
            case links if !links.isEmpty => return links
            case _ =>
        }
        StringParser.parse(node).map(value => (value, new Datatype("xsd:string"))).toList
    }

    private def extractUnitValue(node : PropertyNode) : Option[(String, Datatype)] =
    {
        val unitValues =
        for (unitValueParser <- unitValueParsers;
             (value, unit) <- unitValueParser.parse(node) )
             yield (value, unit)

        if (unitValues.size > 1)
        {
            StringParser.parse(node).map(value => (value, new Datatype("xsd:string")))
        }
        else if (unitValues.size == 1)
        {
            val (value, unit) = unitValues.head
            Some((value.toString, unit))
        }
        else
        {
            None
        }
    }

    private def extractNumber(node : PropertyNode) : Option[(String, Datatype)] =
    {
        intParser.parse(node).foreach(value => return Some((value.toString, new Datatype("xsd:int"))))
        doubleParser.parse(node).foreach(value => return Some((value.toString, new Datatype("xsd:double"))))
        None
    }

    private def extractRankNumber(node : PropertyNode) : Option[(String, Datatype)] =
    {
        StringParser.parse(node) match
        {
            case Some(RankRegex(number)) => Some((number, new Datatype("xsd:int")))
            case _ => None
        }
    }

    private def extractDates(node : PropertyNode) : List[(String, Datatype)] =
    {
        for(date <- extractDate(node))
        {
            return List(date)
        }

        //Split the node. Note that even if some of these hyphens are looking similar, they represent different Unicode numbers.
        val splitNodes = NodeUtil.splitPropertyNode(node, "(—|–|-|&mdash;|&ndash;|,|;)")

        splitNodes.flatMap(extractDate) match
        {
            case dates if dates.size == splitNodes.size => dates
            case _ => List.empty
        }
    }
    
    private def extractDate(node : PropertyNode) : Option[(String, Datatype)] =
    {
        for (dataTimeParser <- dataTimeParsers;
             date <- dataTimeParser.parse(node))
        {
            return Some((date.toString, date.datatype))
        }
        None
    }

    private def extractLinks(node : PropertyNode) : List[(String, Datatype)] =
    {
        val splitNodes = NodeUtil.splitPropertyNode(node, """\s*\W+\s*""")

        splitNodes.flatMap(splitNode => objectParser.parse(splitNode)) match
        {
            case links if links.size == splitNodes.size => return links.map(link => (link, null))
            case _ => List.empty
        }
        splitNodes.flatMap(splitNode => linkParser.parse(splitNode)) match
        {
            case links if links.size == splitNodes.size => return links.map(UriUtils.cleanLink).collect{case Some(link) => (link, null)}
            case _ => List.empty
        }
    }
    
    private def getPropertyUri(key : String) : String =
    {
        // convert property key to camelCase
        var result = key.toLowerCase(extractionContext.language.locale).trim
        result = result.toCamelCase(SplitWordsRegex, extractionContext.language.locale)

        // Replace digits at the beginning of a property with _. E.g. 01propertyName => _01propertyName (edited by Piet)
        result = LeadingNumberRegex.replaceFirstIn(result, "_" + result)

        // Rename Properties like LeaderName1, LeaderName2, ... to LeaderName
        result = TrailingNumberRegex.replaceFirstIn(result, "")

        result = URLEncoder.encode(result, "UTF-8")

        // Decode slash "/", colon ":", as MediaWiki does not encode these
        result = result.replace("%2F", "/")
        result = result.replace("%3A", ":")

        result = result.replace("%", "_percent_")

        // TODO maximal length of properties? (was 250)
        
        OntologyNamespaces.DBPEDIA_GENERAL_NAMESPACE + result
    }

    private def getPropertyLabel(key : String) : String =
    {
        // convert property key to camelCase
        var result = key

        result = result.replace("_", " ")
        
        // Rename Properties like LeaderName1, LeaderName2, ... to LeaderName
        result = TrailingNumberRegex.replaceFirstIn(result, "")

        result
    }

    private def collectTemplates(node : Node) : List[TemplateNode] =
    {
        node match
        {
            case templateNode : TemplateNode => List(templateNode)
            case _ => node.children.flatMap(collectTemplates)
        }
    }

    private def collectProperties(node : Node) : List[PropertyNode] =
    {
        node match
        {
            case propertyNode : PropertyNode => List(propertyNode)
            case _ => node.children.flatMap(collectProperties)
        }
    }
}
