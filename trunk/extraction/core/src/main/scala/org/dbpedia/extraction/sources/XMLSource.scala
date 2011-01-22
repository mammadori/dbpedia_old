package org.dbpedia.extraction.sources

import org.dbpedia.extraction.wikiparser.WikiTitle
import java.io.{FileInputStream, File}
import xml.Elem
import org.dbpedia.extraction.util.Language

/**
 *  Loads wiki pages from an XML stream using the MediaWiki export format.
 * 
 *  The MediaWiki export format is specified by http://www.mediawiki.org/xml/export-0.4
 */
object XMLSource
{
    /**
     * Creates an XML Source from an input stream.
     *
     * @param stream The input stream to read from. Will be closed after reading.
     * @param filter Function to filter pages by their title. Pages for which this function returns false, won't be yielded by the source.
     */
    def fromFile(file : File, filter : (WikiTitle => Boolean) = (title => true)) : Source = new XMLFileSource(file, filter)

    /**
     *  Creates an XML Source from a parsed XML tree.
     *
     * @param xml The xml which contains the pages
     */
    def fromXML(xml : Elem) : Source  = new XMLSource(xml)

    /**
     * XML source which reads from a file
     */
    private class XMLFileSource(file : File, filter : (WikiTitle => Boolean)) extends Source
    {
        override def foreach[U](f : WikiPage => U) : Unit =
        {
            val javaFilter = { title : WikiTitle => filter(title) : java.lang.Boolean }
            val stream = new FileInputStream(file)

            new WikipediaDumpParser(stream, f, javaFilter).run()

            stream.close()
        }

        override def hasDefiniteSize = true
    }

    /**
     * XML source which reads from a parsed XML tree.
     */
    private class XMLSource(xml : Elem) extends Source
    {
        override def foreach[U](f : WikiPage => U) : Unit =
        {
            //TODO set correct language
            val language = Language.Default

            for(page <- xml \ "page";
                rev <- page \ "revision")
            {
                f( new WikiPage( title     = WikiTitle.parse((page \ "title").text, language),
                                 id        = (page \ "id").text.toLong,
                                 revision  = (rev \ "id").text.toLong,
                                 source    = (rev \ "text").text ) )
            }
        }

        override def hasDefiniteSize = true
    }
}
