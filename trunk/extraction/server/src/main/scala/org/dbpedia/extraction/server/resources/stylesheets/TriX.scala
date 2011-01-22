package org.dbpedia.extraction.server.resources.stylesheets

import xml.Elem
import javax.ws.rs.{GET, Produces, Path}

@Path("/stylesheets/trix.xsl")
class TriX
{
    @GET
    @Produces(Array("application/xslt+xml"))
    def get : Elem =
    {
        <xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:trix="http://www.w3.org/2004/03/trix/trix-1/">
          <xsl:template match="/trix:TriX">
            <html>
              <body>
                <h2>DBpedia Extraction Results</h2>
                  <table border="1" cellpadding="3" cellspacing="0">
                    <tr bgcolor="#CCCCFF">
                      <th>Subject</th>
                      <th>Predicate</th>
                      <th>Object</th>
                      <th>Triple Provenance</th>
                    </tr>
                    <xsl:for-each select="trix:graph">
                      <xsl:variable name="context" select="trix:uri"/>
                      <xsl:if test="trix:triple[1]/trix:uri[1] != preceding-sibling::trix:graph[1]/trix:triple[1]/trix:uri[1]">
                        <tr bgcolor="#CCCCFF">
                          <th>Subject</th>
                          <th>Predicate</th>
                          <th>Object</th>
                          <th>Triple Provenance</th>
                        </tr>
                      </xsl:if>
                      <xsl:for-each select="trix:triple">
                        <tr>
                          <td>
                            <xsl:value-of select="*[position()=1]"/>
                          </td>
                          <td>
                            <xsl:value-of select="*[position()=2]"/>
                          </td>
                          <td>
                            <xsl:value-of select="*[position()=3]"/>
                          </td>
                          <td>
                            <a>
                              <xsl:attribute name="href"><xsl:value-of select="substring-before($context, '#')"/></xsl:attribute>
                              <xsl:value-of select="$context"/>
                            </a>
                          </td>
                        </tr>
                      </xsl:for-each>
                  </xsl:for-each>
                </table>
              </body>
            </html>
          </xsl:template>
        </xsl:stylesheet>
    }
}
