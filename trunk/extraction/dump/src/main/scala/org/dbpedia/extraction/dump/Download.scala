package org.dbpedia.extraction.dump

import _root_.org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.net.URL
import java.io._
import java.util.logging.Logger
import _root_.org.dbpedia.extraction.util.StringUtils._
import _root_.org.dbpedia.extraction.util.FileUtils._
import io.{Codec, Source}
import java.util.zip.GZIPInputStream

/**
 * Downloads Wikipedia dumps.
 */
object Download
{
    /**
     * Downloads and updates a list of dumps
     */
    def download(dumpDir : File, wikiCodes : List[String])
    {
        // Download MediaWiki file 'tables.sql'
        DumpDownloader.downloadMWTable(dumpDir)

        // Download Wikipedia dumps
        DumpDownloader.download("commons" :: wikiCodes, dumpDir)
    }

    /**
     * Downloads and updates all dumps with a minimum number of "good" articles
     */
    def download(dumpDir : File, minGoodArticleCount : Int = 10000)
    {
        //Retrieve list of available Wikipedias
        val wikiCodes = WikiInfo.download.filter(_.goodArticleCount >= minGoodArticleCount).map(_.prefix)

        //Download all
        download(dumpDir, wikiCodes)
    }

    /**
     * Run the download from the command line
     *
     * 1st argument: target directory
     *
     * If 2nd argument is an integer: download all Wikipedias that have at least that number of good articles.
     * Else: take all the remaining arguments to be Wikipedia language codes and download all these dumps.
     */
    def main(args : Array[String])
    {
        val dumpDir = new File(args.head)

        try
        {
            val minGoodCount = args(1).toInt
            download(dumpDir, minGoodCount)
        }
        catch
        {
            case e : NumberFormatException =>
            {
                val wikiCodes = args.tail.flatMap(langs => langs.split("\\s+").map(_.trim)).toList
                download(dumpDir, wikiCodes)
            }
        }
    }

/**
 * Downloads Wikipedia dumps from the server.
 */
private object DumpDownloader
{
    private val logger = Logger.getLogger(Download.getClass.getName)

    // The URI where the Wikipedia dumps can be found
    private val downloadUri = "http://download.wikimedia.org"

    // The dump files we are interested in
    private val dumpFiles = List("pages-articles.xml.bz2", "categorylinks.sql.gz", "image.sql.gz",
                                 "imagelinks.sql.gz", "langlinks.sql.gz", "templatelinks.sql.gz")

    /**
     * Downloads MediaWiki file 'tables.sql'
     */
    def downloadMWTable(outputDir : File)
    {
        //URL of table definitions
        val url = "http://svn.wikimedia.org/svnroot/mediawiki/trunk/phase3/maintenance/tables.sql"

        //Create output directory
        outputDir.mkdirs()

        //Open output stream
        val outStream = new PrintStream(outputDir + "/" + "tables.sql", "UTF-8")

        logger.info("Downloading tables.sql to disk")

        //Download files
        Source.fromURL(url, "UTF-8").getLines.foreach(outStream.println(_))

        outStream.close
    }

    /**
     * Downloads a list of MediaWiki dumps.
     */
    def download(wikiCodes : List[String], outputDir : File)
    {
        for(wikiCode <- wikiCodes)
        {
            downloadWiki(wikiCode, new File(outputDir + "/" + wikiCode))
        }
    }

    /**
     * Downloads one MediaWiki dump, which consist of multiple dump files.
     */
    private def downloadWiki(wikiCode : String, dir : File)
    {
        val name = wikiCode.replace('-', '_') + "wiki"
        val date = findMostRecentDate(wikiCode).getOrElse(throw new Exception("No complete dump of " + wikiCode + " found"))
        val url = downloadUri + "/" + name + "/" + date + "/"

        //Delete outdated dumps
        for(subDirs <- Option(dir.listFiles()); subDir <- subDirs; if subDir.getName != date.toString && subDir.getName.matches("\\d{8}") )
        {
            logger.info("Deleting outdated dump " + subDir.getName)
            subDir.deleteRecursive()
        }

        //Generate a list of the expected links to the dump files
        val dumpLinks = dumpFiles.map(dumpFile => name + "-" + date + "-" + dumpFile)

        //Retrieve download page
        //val downloadPage = Source.fromURL(new URL(url))   //TODO REMOOOOOOOOOOOOOOOOOOOOOOVE

        //Create output directory
        val outputDir = new File(dir + "/" + date)
        outputDir.mkdirs()

        //Download all links
        for(link <- dumpLinks)
        {
            val dumpFile = new File(outputDir + "/" + link.substring(0, link.lastIndexOf(".")))
            if(dumpFile.exists)
            {
                logger.info("Found dump file " + dumpFile + " on disk. Keeping this file")
            }
            else
            {
                logger.info("Downloading " + link + " to disk")

                //Download to a temporary file which will be renamed to the destination file afterwards
                val tempFile = new File(dumpFile + ".tmp")

                downloadFile(new URL(url + "/" + link), tempFile)

                if(!tempFile.renameTo(dumpFile))
                {
                    logger.warning("Could not rename file " + tempFile + " to " + dumpFile)
                }
            }
        }
    }

    /**
     * Finds the most recent dump, which provides links to all requested dump files.
     *
     * @param wiki The MediaWiki
     * @return The date of the found dump
     */
    private def findMostRecentDate(wikiCode : String) : Option[Int] =
    {
        val name = wikiCode.replace('-', '_') + "wiki"
        val uri = downloadUri + "/" + name

        //Retrieve download overview page
        val overviewPage = Source.fromURL(new URL(uri)).getLines().mkString

        //Get available dates
        val availableDates = "\\d{8}".r.findAllIn(overviewPage).toList.collect{case IntLiteral(i) => i}

        //Find the first date for which the download page contains all requested dump files
        availableDates.sortWith(_ > _).find(date =>
        {
            //Retrieve the download page for this date
            val downloadPage = Source.fromURL(new URL(uri + "/" + date)).getLines().mkString

            //Generate a list of the expected links to the dump files
            val dumpLinks = dumpFiles.map(dumpFile => name + "-" + date + "-" + dumpFile)

            //Check if this page contains all links
            dumpLinks.forall(link => downloadPage.contains("<a href=\"" + uri + "/" + date + "/" + link))
        })
    }

    /**
     * Downloads a MediaWiki dump file.
     * The file is uncompressed on the fly.
     */
    private def downloadFile(url : URL, file : File)
    {
        val inputStream =
            if (url.toString.endsWith(".gz"))
            {
                new GZIPInputStream(url.openStream())
            }
            else if (url.toString.endsWith(".bz2"))
            {
                new BZip2CompressorInputStream(url.openStream())
            }
            else
            {
                throw new IllegalArgumentException("Unsupported extension: "+url.toString)
            }

        val outputStream = new FileOutputStream(file)
        val buffer = new Array[Byte](65536)

        var totalBytes = 0L
        val startTime = System.nanoTime
        var lastLogTime = 0L

        try
        {
            while(true)
            {
                val bytesRead = inputStream.read(buffer)
                if(bytesRead == -1) return;
                outputStream.write(buffer)
                totalBytes += bytesRead
                val kb = totalBytes / 1024L
                val time = System.nanoTime
                if(time - lastLogTime > 1000000000L)
                {
                    lastLogTime = time
                    println("Uncompressed: " + kb + " KB (" + (kb.toDouble / (time - startTime) * 1000000000.0).toLong + "kb/s)")
                }
            }
        }
        finally
        {
            inputStream.close()
            outputStream.close()
        }
    }
}

/**
* Informations about a MediaWiki.
*/
private case class WikiInfo(prefix : String, language : String, goodArticleCount : Int, totalArticleCount : Int)
{
    override def toString = prefix.replace('-', '_') + ".wikipedia.org"
}

/**
* Retrieves a list of all available Wikipedias.
*/
private object WikiInfo
{
   private val wikiInfoFile = new URL("http://s23.org/wikistats/wikipedias_csv.php")

   /**
    * Retrieves a list of all available Wikipedias.
    */
   def download : List[WikiInfo] =
   {
       val source = Source.fromURL(wikiInfoFile)(Codec.UTF8)
       try
       {
           //Each line (except the first) contains information about a Wikipedia instance
           return source.getLines.toList.tail.filter(!_.isEmpty).map(loadWikiInfo)
       }
       finally
       {
           source.close()
       }
   }

   /**
    * Loads a WikiInfo from a line.
    */
   private def loadWikiInfo(line : String) : WikiInfo = line.split(',').map(_.trim).toList match
   {
       case rank :: id :: prefix :: language :: loclang :: good :: total :: edits :: views :: admins :: users ::
               activeusers :: images :: stubratio :: timestamp :: Nil => new WikiInfo(prefix, language, good.toInt, total.toInt)
       case _ => throw new IllegalArgumentException("Unexpected format in line '" + line + "'")
   }
}

}
