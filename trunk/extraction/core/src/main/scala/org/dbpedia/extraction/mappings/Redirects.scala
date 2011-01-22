package org.dbpedia.extraction.mappings

import java.util.logging.{Level, Logger}
import org.dbpedia.extraction.sources.{WikiPage, Source}
import collection.mutable.{HashSet, HashMap}
import java.io._
import util.control.ControlThrowable
import org.dbpedia.extraction.wikiparser.{WikiParserException, WikiTitle}
import org.dbpedia.extraction.util.Language
import org.dbpedia.extraction.wikiparser.impl.wikipedia.Redirect

/**
 * Holds the redirects between wiki pages
 * At the moment, only redirects between Templates are considered
 *
 * @param map Redirect map. Contains decoded template titles.
 */
//TODO make map private?
//TODO language dependent
class Redirects private(val map : Map[String, String])
{
    /**
     * Resolves a redirect.
     *
     * @param title The title of the page
     * @return If this page is a redirect, the destination of the redirect.
     * If this page is not a redirect, the page itself.
     */
    def resolve(title : WikiTitle) : WikiTitle =
    {
        //Remember already visited pages to avoid cycles
        val visited = new HashSet[String]()

        //Follows redirects
        var currentTitle = title.decoded
        while(!visited.contains(currentTitle))
        {
            visited.add(currentTitle)
            map.get(currentTitle) match
            {
                case Some(destinationTitle) => currentTitle = destinationTitle
                case None => return new WikiTitle(currentTitle, WikiTitle.Namespace.Template, title.language)
            }
        }

        //Detected a cycle
        title
    }

    def resolveMap[T](mappings : Map[String, T]) : Map[String, T] =
    {
        val resolvedMappings = new HashMap[String, T]()

        for((source, destination) <- map if !mappings.contains(source))
        {
            //Remember already visited pages to avoid cycles
            val visited = new HashSet[String]()
            visited.add(source)

            //Compute transitive hull
            var lastDestination = source
            var currentDestination = destination
            while(currentDestination != null && !visited.contains(currentDestination))
            {
                 visited.add(currentDestination)
                 lastDestination = currentDestination
                 currentDestination = map.get(currentDestination).getOrElse(null)
            }

            //Add to redirect map
            for(destinationT <- mappings.get(lastDestination))
            {
                resolvedMappings(source) = destinationT
            }
        }

        return (mappings ++ resolvedMappings)
    }
}

/**
 * Loads redirects from a cache file or source of Wiki pages.
 * At the moment, only redirects between Templates are considered
 */
object Redirects
{
    private val logger = Logger.getLogger(classOf[Redirects].getName)

    //TODO find a general solution for caches
    private val cacheFile = "redirects"

    /**
     * Tries to load the redirects from a cache file.
     * If not successful, loads the redirects from a source.
     * Updates the cache after loading the redirects from the source.
     */
    def load(source : Source, lang : Language = Language.Default) : Redirects =
    {
        //Try to load redirects from the cache
        try
        {
           return loadFromCache(lang)
        }
        catch
        {
            case ex : Exception => logger.log(Level.WARNING, "Could not load redirects from cache. Details: " + ex.getMessage)
        }

        //Load redirects from source
        val redirects = loadFromSource(source, lang)

        //TODO Write redirects to the cache
        //new File(cacheFile).getParentFile.mkdirs()
//        val outputStream = new ObjectOutputStream(new FileOutputStream(cacheFile + "_" + lang.wikiCode))
//        try
//        {
//            outputStream.writeObject(redirects.map)
//        }
//        finally
//        {
//            outputStream.close()
//        }
//        logger.info(redirects.map.size + " redirects written to cache")

        redirects
    }

    /**
     * Loads the redirects from a cache file.
     */
    def loadFromCache(lang : Language = Language.Default) : Redirects =
    {
        logger.info("Loading redirects from cache")
        val inputStream = new ObjectInputStream(Redirects.getClass.getClassLoader.getResourceAsStream(cacheFile + "_" + lang.wikiCode))
        try
        {
            val redirects = new Redirects(inputStream.readObject().asInstanceOf[Map[String, String]])

            logger.info(redirects.map.size + " redirects loaded from cache")
            redirects
        }
        finally
        {
            inputStream.close()
        }
    }

    /**
     * Loads the redirects from a source.
     */
    def loadFromSource(source : Source, lang : Language = Language.Default) : Redirects =
    {
        logger.info("Loading redirects from source")

        val redirectFinder = new RedirectFinder(lang)

        val redirects = new Redirects(source.flatMap(redirectFinder).toMap)

        logger.info("Redirects loaded from source")
        redirects
    }

    private class RedirectFinder(lang : Language) extends (WikiPage => Traversable[(String, String)])
    {
        val regex = ("""(?is)\s*(?:""" + Redirect(lang).getOrElse(Set("#redirect")).mkString("|") + """)\s*:?\s*\[\[([^\]]+)\]\].*""").r

        override def apply(page : WikiPage) =
        {
            page.source match
            {
                case regex(destination) =>
                {
                   try
                   {
                       val destinationTitle = WikiTitle.parse(destination, page.title.language)

                       if(destinationTitle.namespace == WikiTitle.Namespace.Template)
                       {
                           List((page.title.decoded, destinationTitle.decoded))
                       }
                       else
                       {
                           Nil
                       }
                   }
                   catch
                   {
                       case ex : WikiParserException =>
                       {
                           Logger.getLogger(Redirects.getClass.getName).log(Level.WARNING, "Couldn't parse redirect destination", ex)
                           Nil
                       }
                   }
                }
                case _ => Nil
            }
        }
    }
}