package org.dbpedia.extraction.server.resources

import _root_.org.dbpedia.extraction.util.{Language, WikiApi}
import org.dbpedia.extraction.server.Server
import javax.ws.rs._
import java.util.logging.Logger
import org.dbpedia.extraction.wikiparser.WikiTitle
import org.dbpedia.extraction.sources.{WikiSource, XMLSource}
import org.dbpedia.extraction.destinations.StringDestination
import org.dbpedia.extraction.destinations.formatters.TriXFormatter
import java.net.{URI, URL}
import xml.{NodeBuffer, Elem}

/*
 * TODO document input: http://www.mediawiki.org/xml/export-0.4
 * TODO document output: according to the DTD as provided in Appendix A of the Java Logging API specification.
 */
@Path("mappings/{lang}")
class Mappings(@PathParam("lang") langCode : String) extends Base
{
    private val logger = Logger.getLogger(classOf[Ontology].getName)

    private val language = Language.fromWikiCode(langCode)
        .getOrElse(throw new WebApplicationException(404))

    if(!Server.config.languages.contains(language)) throw new WebApplicationException(404)

    /**
     * Retrieves an overview page
     */
    @GET
    @Produces(Array("application/xhtml+xml"))
    def get : Elem =
    {
        <html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en">
          <body>
            <h2>Mappings</h2>
            <a href="pages">Source Pages</a><br/>
            <a href="validate">Validate Pages</a><br/>
            <a href="extractionSamples">Retrieve extraction samples</a><br/>
          </body>
        </html>
    }

    /**
     * Retrieves a mapping page
     */
    @GET
    @Path("/pages")
    @Produces(Array("application/xhtml+xml"))
    def getPages : Elem =
    {
        <html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en">
          <body>
            <h2>Mapping pages</h2>
            { Server.extractor.mappingPages(language).values.map(page => <a href={"pages/" + page.title.encodedWithNamespace}>{page.title}</a><br/>) }
          </body>
        </html>
    }

    /**
     * Retrieves a mapping page
     */
    @GET
    @Path("/pages/{title}")
    @Produces(Array("application/xml"))
    def getPage(@PathParam("title") @Encoded title : String) : Elem =
    {
        logger.info("Get mappings page: " + title)
        Server.extractor.mappingPages(language)(WikiTitle.parseEncoded(title)).toXML
    }

    /**
     * Writes a mapping page
     */
    @PUT
    @Path("/pages/{title}")
    @Consumes(Array("application/xml"))
    def putPage(@PathParam("title") @Encoded title : String, pageXML : Elem) : Unit =
    {
        try
        {
            for(page <- XMLSource.fromXML(pageXML))
            {
                Server.extractor.updateMappingPage(page, language)
                logger.info("Updated mapping page: " + page.title)
            }
        }
        catch
        {
            case ex : Exception =>
            {
                logger.warning("Error updating mapping page: " + title + ". Details: " + ex.getMessage)
                throw ex
            }
        }
    }

    /**
     * Deletes a mapping page
     */
    @DELETE
    @Path("/pages/{title}")
    @Consumes(Array("application/xml"))
    def deletePage(@PathParam("title") @Encoded title : String) : Unit =
    {
        Server.extractor.removeMappingPage(WikiTitle.parseEncoded(title), language)
        logger.info("Deleted mapping page: " + title)
    }

    /**
     * Retrieves the validation overview page
     */
    @GET
    @Path("/validate")
    @Produces(Array("application/xhtml+xml"))
    def validate : Elem =
    {
        <html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en">
          <body>
            <h2>Mapping pages</h2>
            { Server.extractor.mappingPages(language).values.map(page => <a href={"validate/" + page.title.encodedWithNamespace}>{page.title}</a><br/>) }
          </body>
        </html>
    }


    /**
     * Validates a mapping page from the Wiki.
     */
    @GET
    @Path("/validate/{title}")
    @Produces(Array("application/xml"))
    def validateExistingPage(@PathParam("title") @Encoded title : String) =
    {
        var nodes = new NodeBuffer()
        nodes += <?xml-stylesheet type="text/xsl" href="../../../stylesheets/log.xsl"?>
        nodes += Server.extractor.validateMapping(WikiSource.fromTitles(WikiTitle.parseEncoded(title) :: Nil, new URL("http://mappings.dbpedia.org/api.php")), language)
        nodes
    }

    /**
     * Validates a mapping source.
     */
    @POST
    @Path("/validate/{title}")
    @Consumes(Array("application/xml"))
    @Produces(Array("application/xml"))
    def validatePage(@PathParam("title") @Encoded title : String, pagesXML : Elem) =
    {
        try
        {
            var nodes = new NodeBuffer()
            nodes += <?xml-stylesheet type="text/xsl" href="../../../stylesheets/log.xsl"?>
            nodes += Server.extractor.validateMapping(XMLSource.fromXML(pagesXML), language)
            logger.info("Validated mapping page: " + title)
            nodes
        }
        catch
        {
            case ex : Exception =>
            {
                logger.warning("Error validating mapping page: " + title + ". Details: " + ex.getMessage)
                throw ex
            }
        }
    }

    /**
     * Retrieves the extraction overview page
     */
    @GET
    @Path("/extractionSamples")
    @Produces(Array("application/xhtml+xml"))
    def extractionSamples : Elem =
    {
        <html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en">
          <body>
            <h2>Mapping pages</h2>
            { Server.extractor.mappingPages(language).values.map(page => <a href={"extractionSamples/" + page.title.encodedWithNamespace}>{page.title}</a><br/>) }
          </body>
        </html>
    }

    @GET
    @Path("/extractionSamples/{title}")
    @Produces(Array("application/xml"))
    def getExtractionSample(@PathParam("title") @Encoded title : String) : String =
    {
        //Get the title of the mapping as well as its corresponding template on Wikipedia
        val mappingTitle = WikiTitle.parseEncoded(title, language)
        val templateTitle = new WikiTitle(mappingTitle.decoded, WikiTitle.Namespace.Template, mappingTitle.language)
        logger.info("Extraction of samples of '" + templateTitle.encodedWithNamespace + "' requested for language " + language)

        //Find pages which use this mapping
        val wikiApiUrl = new URL("http://" + language.wikiCode + ".wikipedia.org/w/api.php")
        val api = new WikiApi(wikiApiUrl, language)
        val pageTitles = api.retrieveTemplateUsages(templateTitle, 10)

        //Extract pages
        val destination = new StringDestination(new TriXFormatter(new URI("../../../stylesheets/trix.xsl")))
        val source = WikiSource.fromTitles(pageTitles, wikiApiUrl, language)
        Server.extractor.extract(source, destination, language)
        destination.close
        destination.toString
    }
}
