package org.dbpedia.extraction.live.feeder;

import org.apache.commons.collections15.iterators.TransformIterator;
import org.apache.log4j.Logger;
import org.apache.xerces.parsers.DOMParser;
import org.dbpedia.extraction.live.main.Main;
import org.dbpedia.extraction.live.priority.PagePriority;
import org.dbpedia.extraction.live.record.DeletionRecord;
import org.dbpedia.extraction.live.record.IRecord;
import org.dbpedia.extraction.live.record.Record;
import org.dbpedia.extraction.live.transformer.NodeToRecordTransformer;
import org.dbpedia.extraction.live.util.ExceptionUtil;
import org.dbpedia.extraction.live.util.LastResponseDateManager;
import org.dbpedia.extraction.live.util.OAIUtil;
import org.dbpedia.extraction.live.util.XMLUtil;
import org.dbpedia.extraction.live.util.iterators.TimeWindowIterator;
import org.w3c.dom.Document;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Iterator;

import org.w3c.dom.NodeList;

/**
 * Created by IntelliJ IDEA.
 * User: Mohamed Morsey
 * Date: Jul 28, 2010
 * Time: 2:48:44 PM
 * This class gets a list of all page IDs that are updated recently and must be reprocessed
 * feeds those page IDs to a common priority pageQueue for further processing
 */
public class LiveUpdateFeeder extends Thread{

    private static Logger logger = Logger.getLogger(LiveUpdateFeeder.class);

    String oaiUri = "http://en.wikipedia.org/wiki/Special:OAIRepository";

    Calendar calendar = new GregorianCalendar();

    //Date startDate = calendar.getTime();
    String startDate = "2010-06-01T15:00:00Z";

    int pollInterval = 30;
    int sleepInterval = 5;
    public static String lastResponseDateFile = "./live/lastResponseDate.dat";

    int articleDelay = 0;
    boolean articleRenewal = false;
    String baseWikiUri = "http://en.wikipedia.org/wiki/";
    String oaiPrefix = "oai:en.wikipedia.org:enwiki:";

    //This parser is used to parse the XML text generated from the page, in order to get the PageID
    DOMParser parser = new DOMParser();

//    public LiveUpdateFeeder(){
//        super("Live extraction feeder");
//
//        start();
//
//    }

    public LiveUpdateFeeder(String name, int priority){

        this.setPriority(priority);
        this.setName(name);
        calendar.set(2010, 06, 10, 22, 0, 0);

        String proposedStartDate = LastResponseDateManager.getLastResponseDate(lastResponseDateFile);
        if(proposedStartDate != null)
           startDate = proposedStartDate;

        start();
    }

    public LiveUpdateFeeder(String name){
        this(name, Thread.NORM_PRIORITY);
    }

    public LiveUpdateFeeder(){
        this("LiveUpdateFeeder", Thread.NORM_PRIORITY);
    }


    public void run() {

        // Create an iterator which keeps polling the OAIRepository
		Iterator<Document> recordIterator =
			OAIUtil.createEndlessRecordIterator(oaiUri, startDate, pollInterval * 1000, sleepInterval * 1000);

        while(recordIterator.hasNext())
        {
            try
            {
                Document doc = recordIterator.next();

                //Extract the page identifier from the XML returned. It is available in a tag <identifier>
                NodeList nodes = doc.getElementsByTagName("identifier");
                String strFullPageIdentifier = nodes.item(0).getChildNodes().item(0).getNodeValue();
                startDate = XMLUtil.getPageModificationDate(doc);

                //We should  extract only the pageID from the returned identifier as the identifier is on
                //the form  oai:en.wikipedia.org:enwiki:1234
                int colonPos = strFullPageIdentifier.lastIndexOf(":");
                String strPageID = strFullPageIdentifier.substring(colonPos+1);

                long pageID = new Long(strPageID);

                Main.pageQueue.add(new PagePriority(pageID, false, startDate));
                String lastResponseDate = XMLUtil.getPageModificationDate(doc);

                //LastResponseDateManager.writeLastResponseDate(lastResponseDateFile, lastResponseDate);
            }
            catch(Exception exp)
            {
                logger.error(ExceptionUtil.toString(exp));
            }

        }

		// Optional: Create an iterator which does not return elements unless
		// they have reached a certain age.
		// Concretely for Wikipedia articles this means:
		// Only return articles that have not been edited for at least x seconds
		TimeWindowIterator timeWindowIterator = null;
		if(articleDelay != 0) {
			timeWindowIterator = new TimeWindowIterator(recordIterator, articleDelay, false, articleRenewal);
			recordIterator = timeWindowIterator;
		}

		// Transform the XML fragments to Java domain objects
        //This iterator is not needed any more
		Iterator<IRecord> iterator = new TransformIterator<Document, IRecord>(recordIterator, new NodeToRecordTransformer(baseWikiUri, oaiUri, oaiPrefix));


    		while(iterator.hasNext()) {
			IRecord record = iterator.next();


			if(record instanceof Record) {

			}
			else if(record instanceof DeletionRecord) {
				DeletionRecord x = (DeletionRecord)record;
				System.out.println(x.getOaiId());
			}
			else {
				throw new RuntimeException("Should not happen");
			}
		}
    }


}
