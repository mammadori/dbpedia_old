package org.dbpedia.extraction.live.extraction

import org.dbpedia.extraction.sources.{XMLSource, WikiSource}
import org.dbpedia.extraction.sources.LiveExtractionXMLSource
import xml.Elem

import java.util.Properties;
import java.io.File

import org.dbpedia.extraction.wikiparser.WikiTitle

import java.io._;

/**
 * Created by IntelliJ IDEA.
 * User: Mohamed Morsey
 * Date: May 26, 2010
 * Time: 4:15:28 PM
 * This is the main class responsible for managing the process of live extraction.
 */

object LiveExtractionManager
{
  var wiki : WikiTitle = null;
  var oaiID : String = "";
  var Num=1;


  
  //Setting and loading the configuration file
  private val configFile = new File("./live/liveconfig.properties");
//  LiveExtractionConfigLoader.loadConfig(configFile);

  //Load properties
  val properties = new Properties();
  properties.load(new FileReader(configFile));

 
  def extractFromPage(Element :scala.xml.Elem)
    {
      val articlesSource = LiveExtractionXMLSource.fromXML(Element);
      //val extractor = Extractor.startExtraction(config.ontologySource, config.mappingsSource, emptySource, articlesSource, config.extractors(Language.Default), Language.Default)

      LiveExtractionConfigLoader.startExtraction(articlesSource);
      /*val extractionJobs = LiveExtractionConfigLoader.startExtraction(articlesSource);
      println("Number of extraction jobs = " + extractionJobs.size)
      for(extractionJob <- extractionJobs)
      {
        extractionJob.start();
      }*/

    }

  /*private class Config(config : Properties)
    {
        /** Resources directory */
        //TODO remove?
//        private val resourcesDir = new File("./src/main/resources")
//        if(!resourcesDir.exists) throw new IllegalArgumentException("Resource directory not found in " + resourcesDir.getCanonicalPath)

        /** Dump directory */
        if(config.getProperty("dumpDir") == null) throw new IllegalArgumentException("Property 'dumpDir' not defined.")
        val dumpDir = new File(config.getProperty("dumpDir"))

        /** Output directory */
        if(config.getProperty("outputDir") == null) throw new IllegalArgumentException("Property 'outputDir' not defined.")
        val outputDir = new File(config.getProperty("outputDir"))

        /** Languages */
        if(config.getProperty("languages") == null) throw new IllegalArgumentException("Property 'languages' not defined.")
        private val languages = config.getProperty("languages").split("\\s+").map(_.trim).toList
                        .map(code => Language.fromWikiCode(code).getOrElse(throw new IllegalArgumentException("Invalid language: '" + code + "'")))

        /** Extractor classes */
        val extractors = loadExtractorClasses()

        /** Ontology source */
        val ontologySource = WikiSource.fromNamespaces(namespaces = scala.collection.immutable.Set(WikiTitle.Namespace.OntologyClass, WikiTitle.Namespace.OntologyProperty),
                                                       url = new URL("http://mappings.dbpedia.org/api.php"),
                                                       language = Language.Default )

        /** Mappings source */
        val mappingsSource =  WikiSource.fromNamespaces(namespaces = scala.collection.immutable.Set(WikiTitle.Namespace.Mapping),
                                                        url = new URL("http://mappings.dbpedia.org/api.php"),
                                                        language = Language.Default )

        /** Commons source */
        val commonsSource = XMLSource.fromFile(getDumpFile("commons"), _.namespace == WikiTitle.Namespace.File)

        /**
         * Retrieves the dump stream for a specific language edition.
         */
        def getDumpFile(wikiPrefix : String) : File =
        {
            val wikiDir = new File(dumpDir + "/" + wikiPrefix)
            if(!wikiDir.exists) throw new Exception("Dump directory not found: " + wikiDir)

            //Find most recent dump date
            val date = wikiDir.list()
                       .filter(_.matches("\\d{8}"))
                       .sortWith(_.toInt > _.toInt)
                       //.headOption.getOrElse(throw new Exception("No dump found for Wiki: " + wikiPrefix))
                        .headOption.getOrElse("")

            val articlesDump = new File(wikiDir + "/" + date + "/" + wikiPrefix.replace('-', '_') + "wiki-" + date + "-pages-articles.xml")
            //if(!articlesDump.exists) throw new Exception("Dump not found: " + articlesDump)

            articlesDump;
        }

  private def loadExtractorClasses() : scala.collection.immutable.Map[Language, scala.collection.immutable.List[Class[Extractor]]] =
        {
            //Load extractor classes
            if(config.getProperty("extractors") == null) throw new IllegalArgumentException("Property 'extractors' not defined.")
            val stdExtractors = loadExtractorConfig(config.getProperty("extractors"))

            //Create extractor map
            var extractors = scala.collection.immutable.ListMap[Language, scala.collection.immutable.List[Class[Extractor]]]()
            for(language <- languages) extractors += ((language, stdExtractors))

            //Load language specific extractors
            val LanguageExtractor = "extractors\\.(.*)".r

            for(LanguageExtractor(code) <- config.stringPropertyNames.toArray;
                language = Language.fromISOCode(code).getOrElse(throw new IllegalArgumentException("Invalid language: " + code));
                if extractors.contains(language))
            {
                extractors += ((language, stdExtractors ::: loadExtractorConfig(config.getProperty("extractors." + code))))
            }

            extractors
        }

        /**
         * Parses a enumeration of extractor classes.
         */
        private def loadExtractorConfig(configStr : String) : scala.collection.immutable.List[Class[Extractor]] =
        {
            configStr.split("\\s+").map(_.trim).toList
            .map(className => ClassLoader.getSystemClassLoader().loadClass(className))
            .map(_.asInstanceOf[Class[Extractor]])
        }
    }*/
}