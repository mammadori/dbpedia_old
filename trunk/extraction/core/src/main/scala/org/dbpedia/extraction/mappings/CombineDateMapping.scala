package org.dbpedia.extraction.mappings

import org.dbpedia.extraction.ontology.OntologyProperty
import org.dbpedia.extraction.ontology.datatypes.Datatype
import org.dbpedia.extraction.wikiparser.{NodeUtil, TemplateNode}
import java.util.logging.Logger
import org.dbpedia.extraction.dataparser.DateTimeParser
import org.dbpedia.extraction.util.Date
import org.dbpedia.extraction.destinations.{DBpediaDatasets, Quad, Graph}

class CombineDateMapping( ontologyProperty : OntologyProperty,
                          templateProperty1 : String,
                          unit1 : Datatype,
                          templateProperty2 : String,
                          unit2 : Datatype,
                          templateProperty3 : String,
                          unit3 : Datatype,
                          extractionContext : ExtractionContext ) extends PropertyMapping
{
    require(Set("xsd:date", "xsd:gDay", "xsd:gMonth", "xsd:gYear", "xsd:gMonthDay", "xsd:gYearMonth").contains(ontologyProperty.range.name),
        "ontologyProperty must be one of: xsd:date, xsd:gDay, xsd:gMonth, xsd:gYear, xsd:gMonthDay, xsd:gYearMonth")

    private val logger = Logger.getLogger(classOf[CombineDateMapping].getName)

    private val parser1 = Option(unit1).map(new DateTimeParser(extractionContext, _))
    private val parser2 = Option(unit2).map(new DateTimeParser(extractionContext, _))
    private val parser3 = Option(unit3).map(new DateTimeParser(extractionContext, _))

    override def extract(node : TemplateNode, subjectUri : String, pageContext : PageContext) : Graph =
    {
        var dates = List[Date]()
        for( parser <- parser1;
             property1 <- node.property(templateProperty1);
             parseResult1 <- parser.parse(property1) )
        {
            dates ::= parseResult1
        }
        for( parser <- parser2;
             property2 <- node.property(templateProperty2);
             parseResult2 <- parser.parse(property2) )
        {
            dates ::= parseResult2
        }
        for( parser <- parser3;
             property3 <- node.property(templateProperty3);
             parseResult3 <- parser.parse(property3) )
        {
            dates ::= parseResult3
        }

       /* property2 <- node.property(templateProperty2);
             property3 <- node.property(templateProperty3);

    parseResult2 <- parser.parse(property2);
             parseResult3 <- parser.parse(property3)
          */

        val datatype = ontologyProperty.range.asInstanceOf[Datatype]

        try
        {
            val mergedDate = Date.merge(dates, datatype)

            val quad = new Quad(extractionContext, DBpediaDatasets.OntologyProperties, subjectUri, ontologyProperty, mergedDate.toString, node.sourceUri, datatype)

            new Graph(quad)
        }
        catch
        {
            case ex : Exception => new Graph()
        }
    }
}