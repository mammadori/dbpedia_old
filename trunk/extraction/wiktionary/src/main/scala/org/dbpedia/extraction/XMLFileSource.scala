package org.dbpedia.extraction


import collection.JavaConversions._
import sources.{WikiPage, WikipediaDumpParser, Source}
import wikiparser.WikiTitle
import org.springframework.core.io.Resource

/**
 * @author Sebastian Hellmann <hellmann@informatik.uni-leipzig.de>
 */

class XMLFileSource(resource: Resource, filter: (WikiTitle => Boolean)) extends Source
{
  def this(resource: Resource, namespaceNumbers: java.util.List[java.lang.Integer]) = {
    this (resource, title => {
      //val ret = false;
      //JListWrapper(namespaceNumbers).contains(new java.lang.Integer(title.namespace))
      true
    })
  }


  override def foreach[U](f: WikiPage => U): Unit =
  {
    val javaFilter = {
      title: WikiTitle => filter(title): java.lang.Boolean
    }
    val stream = resource.getInputStream
    new WikipediaDumpParser(stream, f, javaFilter).run()
    stream.close()
  }

  override def hasDefiniteSize = true
}