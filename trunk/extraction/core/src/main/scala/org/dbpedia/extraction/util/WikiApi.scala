package org.dbpedia.extraction.util

import java.util.logging.Logger
import java.io.IOException
import xml.{XML, Elem}
import org.dbpedia.extraction.wikiparser.WikiTitle
import org.dbpedia.extraction.wikiparser.WikiTitle.Namespace
import org.dbpedia.extraction.sources.WikiPage
import java.net.{URLEncoder, URL}
import runtime.Long

/**
 * Executes queries to the MediaWiki API.
 *
 * @param url The URL of the MediaWiki API e.g. http://en.wikipedia.org/w/api.php. Default: english Wikipedia
 * @param language The language of the MediaWiki.
 */
class WikiApi(url : URL = new URL("http://en.wikipedia.org/w/api.php"), language : Language = Language.Default)
{
    private val logger = Logger.getLogger(classOf[WikiApi].getName)

    /** The number of retries before a query is considered as failed */
    private val maxRetries = 10

    /** The number of pages, which are listed per request. MediaWikis usually limit this to a maximum of 500. */
    private val pageListLimit = 500

    /** The number of pages which are downloaded per request. MediaWikis usually limit this to a maximum of 50. */
    private val pageDownloadLimit = 50

    /**
     * Retrieves all pages with a specific namespace starting from a specific page.
     *
     * @param namespace The namespace of the requested pages.
     * @param fromPage The page title to start enumerating from.
     * @param f The function to be called on each page.
     */
    def retrievePagesByNamespace[U](namespace : Namespace, f : WikiPage => U, fromPage : String = "")
    {
        //Retrieve list of pages
        val response = query("?action=query&format=xml&list=allpages&apfrom=" + fromPage + "&aplimit=" + pageListLimit + "&apnamespace=" + namespace.id)

        //Extract page ids
        val pageIds = for(p <- response \ "query" \ "allpages" \ "p") yield (p \ "@pageid").head.text.toLong

        //Retrieve pages
        retrievePagesByID(pageIds).foreach(f)

        //Retrieve remaining pages
        for(continuePage <- response \ "query-continue" \ "allpages" \ "@apfrom" headOption)
        {
            retrievePagesByNamespace(namespace, f, continuePage.text)
        }
    }

    /**
     * Retrieves multiple pages by their ID.
     *
     * @param pageIds The IDs of the pages to be downloaded.
     * @param f The function to be called on each page.
     */
    def retrievePagesByID[U](pageIds : Iterable[Long]) = new Traversable[WikiPage]
    {
        override def foreach[U](f : WikiPage => U) : Unit =
        {
            for(ids <- pageIds.grouped(pageDownloadLimit))
            {
                val response = query("?action=query&format=xml&prop=revisions&pageids=" + ids.mkString("|") + "&rvprop=ids|content")

                for(page <- response \ "query" \ "pages" \ "page";
                    rev <- page \ "revisions" \ "rev" )
                {
                    f( new WikiPage( title     = WikiTitle.parse((page \ "@title").head.text, language),
                                     id        = (page \ "@pageid").head.text.toLong,
                                     revision  = (rev \ "@revid").head.text.toLong,
                                     source    = rev.text ) )
                }
            }
        }
    }

    /**
     * Retrieves multiple pages by their title.
     *
     * @param pageIds The titles of the pages to be downloaded.
     * @param f The function to be called on each page.
     */
    def retrievePagesByTitle[U](titles : Traversable[WikiTitle]) = new Traversable[WikiPage]
    {
        override def foreach[U](f : WikiPage => U) : Unit =
        {
            for(titleGroup <- titles.toIterable.grouped(pageDownloadLimit))
            {
                val response = query("?action=query&format=xml&prop=revisions&titles=" + titleGroup.map(_.encodedWithNamespace).mkString("|") + "&rvprop=ids|content")

                for(page <- response \ "query" \ "pages" \ "page";
                    rev <- page \ "revisions" \ "rev" )
                {
                    f( new WikiPage( title     = WikiTitle.parse((page \ "@title").head.text, language),
                                     id        = (page \ "@pageid").head.text.toLong,
                                     revision  = (rev \ "@revid").head.text.toLong,
                                     source    = rev.text ) )
                }
            }
        }
    }

    /**
     * Retrieves a list of pages which use a given template.
     *
     * @param title The title of the template
     * @param maxCount The maximum number of pages to retrieve
     */
    def retrieveTemplateUsages(title : WikiTitle, maxCount : Int = 500) : Traversable[WikiTitle] =
    {
        val response = query("?action=query&format=xml&list=embeddedin&eititle=" + title.encodedWithNamespace + "&einamespace=0&eifilterredir=nonredirects&eilimit=" + maxCount)

        for(page <- response \ "query" \ "embeddedin" \ "ei";
            title <- page \ "@title" )
            yield new WikiTitle(title.text)
    }

  /**
   * Returns a list of page IDs fo the pages for a certain wiki title
   * @param title The title of the wiki
   * @param maxCount  the maximum number of matches
   * @return  A list of page IDs  
   */
  def retrieveTemplateUsageIDs(title : WikiTitle, maxCount : Int = 500) : List[Long] =
    {
        var pageList = List[Long]();
        var  canContinue = false;
        var eicontinue = "";
        var appropriateQuery = "";

        do{
          appropriateQuery = "?action=query&format=xml&list=embeddedin&eititle=" + title.encodedWithNamespace +
                              "&einamespace=0&eifilterredir=nonredirects&eilimit=" + maxCount;
          //Since the call can return only 500 matches at most we must use the eicontinue parameter to
          //get the other matches
          if(canContinue)
            appropriateQuery = appropriateQuery + "&eicontinue=" + eicontinue;

          val response = query(appropriateQuery);

            val queryContinue = response \ "query-continue" \ "embeddedin";
            val continueSeq = queryContinue \\ "@eicontinue";
            eicontinue = continueSeq.text;

            canContinue = false;

            if((eicontinue != null) && (eicontinue != ""))
              canContinue= true;

            for(page <- response \ "query" \ "embeddedin" \ "ei";
                title <- page \ "@pageid" ){
                 pageList = pageList ::: List(title.text.toLong);
            }
        }while(canContinue)

      pageList;
    }

    /**
     * Executes a query to the MediaWiki API.
     */
    protected def query(params : String) : Elem =
    {
        for(i <- 0 to maxRetries)
        {
            try
            {
                val reader = new URL(url + params).openStream()
                val xml = XML.load(reader)
                reader.close()

                return xml
            }
            catch
            {
                case ex : IOException =>
                {
                    if(i < maxRetries - 1)
                    {
                        logger.fine("Query failed: " + params + ". Retrying...")
                    }
                    else
                    {
                        throw ex
                    }
                }
            }

            Thread.sleep(100)
        }

        throw new IllegalStateException("Should never get there")
    }
}