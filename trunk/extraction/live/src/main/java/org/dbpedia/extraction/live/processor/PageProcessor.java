package org.dbpedia.extraction.live.processor;

import ORG.oclc.oai.harvester2.verb.GetRecord;
import org.apache.log4j.Logger;
import org.dbpedia.extraction.live.extraction.LiveExtractionManager;
import org.dbpedia.extraction.live.feeder.LiveUpdateFeeder;
import org.dbpedia.extraction.live.feeder.MappingUpdateFeeder;
import org.dbpedia.extraction.live.priority.PagePriority;
import org.dbpedia.extraction.live.util.LastResponseDateManager;
import org.dbpedia.extraction.live.util.XMLUtil;
import org.dbpedia.extraction.sources.Source;
import org.dbpedia.extraction.sources.XMLSource;
import org.dbpedia.extraction.live.main.Main;


import org.w3c.dom.Document;
import scala.xml.*;

/**
 * Created by IntelliJ IDEA.
 * User: Mohamed Morsey
 * Date: Jul 28, 2010
 * Time: 3:43:57 PM
 * This class dequeues the item at the front of the priority pageQueue and processes it i.e. apply the extraction
 * process on it.
 */
public class PageProcessor extends Thread{
    
    private static Logger logger = Logger.getLogger(PageProcessor.class);

    public PageProcessor(String name, int priority){
        this.setPriority(priority);
        this.setName(name);
        start();
    }

    public PageProcessor(String name){
        this(name, Thread.NORM_PRIORITY);
    }

    public PageProcessor(){
        this("PageProcessor", Thread.NORM_PRIORITY);
    }

    private void processPage(long pageID){
        try{
            String oaiUri = "http://en.wikipedia.org/wiki/Special:OAIRepository";
            String oaiPrefix = "oai:en.wikipedia.org:enwiki:";
            String mediaWikiPrefix = "mediawiki";

            GetRecord record = new GetRecord(oaiUri, oaiPrefix + pageID, mediaWikiPrefix);

            Document doc = record.getDocument();
            Elem element = (Elem) XML.loadString(XMLUtil.toString(doc));
            Source wikiPageSource = XMLSource.fromXML(element);

            LiveExtractionManager.extractFromPage(element);

        }
        catch(Exception exp){
            logger.error("Error in processing page number " + pageID + ", and the reason is " + exp.getMessage());
        }

    }


    public void run(){
        while(true){
            if(!Main.pageQueue.isEmpty()){
                PagePriority requiredPage = Main.pageQueue.peek();
                System.out.println("Page # " + requiredPage + " has been removed and processed");
                processPage(requiredPage.pageID);
                Main.pageQueue.remove();

                //Write response date to file in both cases of live update and mapping update
                if(requiredPage.isResultOfMappingChange)
                    LastResponseDateManager.writeLastResponseDate(MappingUpdateFeeder.lastResponseDateFile,
                            requiredPage.lastResponseDate);
                else
                    LastResponseDateManager.writeLastResponseDate(LiveUpdateFeeder.lastResponseDateFile,
                                        requiredPage.lastResponseDate);

            }

        }
        
    }

}
