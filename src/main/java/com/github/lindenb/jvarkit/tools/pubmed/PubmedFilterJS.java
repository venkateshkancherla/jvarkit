/*
The MIT License (MIT)

Copyright (c) 2014 Pierre Lindenbaum

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.


History:
* 2014 creation

*/
package com.github.lindenb.jvarkit.tools.pubmed;


import gov.nih.nlm.ncbi.pubmed.ObjectFactory;
import gov.nih.nlm.ncbi.pubmed.PubmedArticle;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.PrintWriter;
import java.util.List;

import javax.script.CompiledScript;
import javax.script.SimpleBindings;
import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLResolver;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.XMLEvent;
import javax.xml.transform.stream.StreamSource;

import com.beust.jcommander.Parameter;
import com.github.lindenb.jvarkit.util.jcommander.Launcher;
import com.github.lindenb.jvarkit.util.jcommander.Program;
import com.github.lindenb.jvarkit.util.log.Logger;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;


/**
 * PubmedFilterJS
 *
 */
@Program(name="pubmedfilterjs",description="Filters Pubmed XML with a javascript  (java rhino) expression. Context contain 'article' a  PubmedBookArticle or a PubmedArticle and 'index', the index in the XML file.")
public class PubmedFilterJS
	extends Launcher
	{
	private static final Logger LOG = Logger.build(PubmedFilterJS.class).make();
	@Parameter(names={"-o","--output"},description="Output file. Optional . Default: stdout")
	private File outFile=null;

	@Parameter(names={"-f","--scriptfile"},description="Javascript file")
	private File javascriptFile=null;
	@Parameter(names={"-e","--expression"},description="Javascript expression")
	private String javascriptExpr=null;

	
	@SuppressWarnings("unused")
	private static ObjectFactory _fool_javac=null;
	
	public PubmedFilterJS()
		{
		
		}
	
	
	

	@Override
	public int doWork(List<String> args) {
		final String inputName= oneFileOrNull(args);
		CompiledScript compiledScript=null;
		Unmarshaller unmarshaller;
		Marshaller marshaller;
		try
			{
			compiledScript =  this.compileJavascript(
					this.javascriptExpr,
					this.javascriptFile
					);
						
			JAXBContext jc = JAXBContext.newInstance("gov.nih.nlm.ncbi.pubmed");
			unmarshaller =jc.createUnmarshaller();
			marshaller =jc.createMarshaller();

			
			XMLInputFactory xmlInputFactory=XMLInputFactory.newFactory();
			xmlInputFactory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, Boolean.FALSE);
			xmlInputFactory.setProperty(XMLInputFactory.IS_COALESCING, Boolean.TRUE);
			xmlInputFactory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, Boolean.TRUE);
			xmlInputFactory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
			xmlInputFactory.setXMLResolver(new XMLResolver() {
				@Override
				public Object resolveEntity(String publicID, String systemID, String baseURI, String namespace)
						throws XMLStreamException {
					LOG.info("ignoring "+publicID+" "+baseURI+" "+namespace);
					return new ByteArrayInputStream(new byte[0]);
				}
			});
			marshaller.setProperty(Marshaller.JAXB_FRAGMENT, true);
			marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);

			
			PrintWriter pw= openFileOrStdoutAsPrintWriter(this.outFile);
			XMLOutputFactory xof=XMLOutputFactory.newFactory();
			XMLEventWriter w=xof.createXMLEventWriter(pw);
			
			
			StreamSource src=null;
			if(inputName==null)
				{
				LOG.info("Reading stdin");
				src=new StreamSource(System.in);
				}
			else 
				{
				LOG.info("Reading file");
				src=new StreamSource(new File(inputName));
				}
			
			XMLEventReader r=xmlInputFactory.createXMLEventReader(src);
			
			XMLEventFactory eventFactory=XMLEventFactory.newFactory();
			
			SimpleBindings bindings=new SimpleBindings();
			long nArticles=0L;
			while(r.hasNext())
				{
				XMLEvent evt=r.peek();
				switch(evt.getEventType())
					{
					case XMLEvent.START_ELEMENT:
						{
						String localName= evt.asStartElement().getName().getLocalPart();
						Object article=null;
						JAXBElement<?> jaxbElement=null;
						if(localName.equals("PubmedArticle"))
							{
							jaxbElement= unmarshaller.unmarshal(r,PubmedArticle.class);
							article=jaxbElement.getValue();
							}
						/* no more in the latest dtd else if(localName.equals("PubmedBookArticle"))
							{
							jaxbElement= unmarshaller.unmarshal(r,PubmedBookArticle.class);
							article=jaxbElement.getValue();
							} */
						else
							{
							w.add(r.nextEvent());
							break;
							}
						
						if(article!=null)
							{
							
							bindings.put("article", article);
							bindings.put("index", nArticles++);
							
							if(!this.evalJavaScriptBoolean(compiledScript, bindings))
								{
								break;
								}
							marshaller.marshal(jaxbElement, w);
							w.add(eventFactory.createCharacters("\n"));
							}
						
						break;
						}
					default:
						{
						w.add(r.nextEvent());
						break;
						}
					}
				r.close();
				}
			w.flush();
			w.close();
			pw.flush();
			pw.close();
			return 0;
			}
		catch(Exception err)
			{
			LOG.error(err);
			return -1;
			}
		finally
			{
			
			}
		}
	
	public static void main(String[] args) {
		new PubmedFilterJS().instanceMainWithExit(args);
	}
}
