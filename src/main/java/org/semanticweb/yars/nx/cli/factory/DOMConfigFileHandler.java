package org.semanticweb.yars.nx.cli.factory;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.semanticweb.yars.nx.BNode;
import org.semanticweb.yars.nx.Literal;
import org.semanticweb.yars.nx.Node;
import org.semanticweb.yars.nx.Nodes;
import org.semanticweb.yars.nx.Resource;
import org.semanticweb.yars.nx.filter.FilterIterator;
import org.semanticweb.yars.nx.filter.NodeFilter;
import org.semanticweb.yars.nx.filter.NodeFilter.AbstractFilter;
import org.semanticweb.yars.nx.filter.NodeFilter.AndFilter;
import org.semanticweb.yars.nx.filter.NodeFilter.ClassFilter;
import org.semanticweb.yars.nx.filter.NodeFilter.EqualsFilter;
import org.semanticweb.yars.nx.filter.NodeFilter.OrFilter;
import org.semanticweb.yars.nx.filter.NodeFilter.RegexFilter;
import org.semanticweb.yars.nx.parser.NxParser;
import org.semanticweb.yars.nx.parser.ParseException;
import org.semanticweb.yars.stats.CountStmtAnalyser;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class DOMConfigFileHandler{
	String _doc;
	Document _dom;
	Iterator<Node[]> _currentIter = null;
	NodeFilter[] _currentFilt = null;
	boolean _input = true;
	private static final int DEFAULT_STMT_LENGTH = 4;
	
	public DOMConfigFileHandler(String doc){
		_doc = doc;
	}
	
	public void parse() throws SAXException, IOException, ParserConfigurationException{
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		DocumentBuilder db = dbf.newDocumentBuilder();
		_dom = db.parse(_doc);
		
	}
	
	public Iterator<Node[]> create() throws DOMException, ParseException, IOException, ConfigFileParseException{
		NodeList nl = _dom.getChildNodes();
		handleIteratorNode(nl.item(0));
		
		return _currentIter;
	}

	private void handleIteratorNode(org.w3c.dom.Node n) throws DOMException, ParseException, IOException, ConfigFileParseException{
		NamedNodeMap nnm = n.getAttributes();
		if(nnm==null){
			throw new ConfigFileParseException("Missing iterator attributes.");
		}
		org.w3c.dom.Node type = nnm.getNamedItem("type");
		
		if(type==null){
			throw new ConfigFileParseException("No type attribute.");
		} else{
			String t = type.getNodeValue();
			if(t.equals("input")){
				if(_currentIter!=null){
					throw new ConfigFileParseException("Iterator type 'input' only allowed as root element.");
				}

				org.w3c.dom.Node file = nnm.getNamedItem("file");
				if(file==null){
					_currentIter = new NxParser(System.in);
				} else{
					FileInputStream fis = new FileInputStream(file.getNodeValue());
					_currentIter = new NxParser(fis);
				}
				
				_input = true;
				
				org.w3c.dom.Node next = handleIteratorChildNode(n);
				if(_currentFilt!=null){
					_currentIter = new FilterIterator(_currentIter, _currentFilt);
					_currentFilt = null;
				}
				
				if(next!=null){
					handleIteratorNode(next);
				}
			} else{ 
				if(_currentIter==null){
					_currentIter = new NxParser(System.in);
					_input = true;
				}
				
				if(t.equals("countStmt")){
					org.w3c.dom.Node next = handleIteratorChildNode(n);
					_currentIter = new CountStmtAnalyser(_currentIter, _currentFilt);
					_currentFilt = null;
					if(next!=null){
						handleIteratorNode(next);
					}
				} else if(t.equals("blah")){
					
				}
			}
		}
	}
	
	private org.w3c.dom.Node handleIteratorChildNode(org.w3c.dom.Node n) throws ConfigFileParseException{
		NodeList children = n.getChildNodes();
		org.w3c.dom.Node nextIter = null;
		for(int i=0; i<children.getLength(); i++){
			org.w3c.dom.Node child = children.item(i);
			if(child.getNodeName().equals("iterator")){
				nextIter = child;
			} else if(child.getNodeName().equals("filterStmt")){
				_currentFilt = handleFilterStmt(child);
			}
		}
		return nextIter;
	}
	
	private NodeFilter[] handleFilterStmt(org.w3c.dom.Node n) throws ConfigFileParseException{
		NodeList children = n.getChildNodes();
		NodeFilter[] nfs = initialiseNodeFilterArray();
		for(int i=0; i<children.getLength(); i++){
			org.w3c.dom.Node child = children.item(i);
			if(child.getNodeName().equals("filterElement")){
				NamedNodeMap attrs = child.getAttributes();
				if(attrs==null){
					throw new ConfigFileParseException("Missing 'index' attribute for filterElement");
				}
				org.w3c.dom.Node index = attrs.getNamedItem("index");
				if(index==null){
					throw new ConfigFileParseException("Missing 'index' attribute for filterElement");
				}
				int ind = Integer.parseInt(index.getNodeValue());
				ind--;
				if(ind>=nfs.length){
					nfs = increaseCapacity(nfs, ind+1);
				}else if(nfs[ind]!=null){
					throw new ConfigFileParseException("Multiple filterElement defined for index "+(ind+1));
				}
				
				nfs[ind] = handleFilterElement(child);
			} else if(isIgnorable(child)){
				;
			} else{
				throw new ConfigFileParseException("Illegal child node of filterStmt: "+child+". Only 'filterElement' allowed.");
			}
		}
		return nfs;
	}
	
	private boolean isIgnorable(org.w3c.dom.Node n){
		if(n.getNodeType()==org.w3c.dom.Node.TEXT_NODE && n.getNodeValue().trim().isEmpty())
			return true;
		else if(n.getNodeType()==org.w3c.dom.Node.COMMENT_NODE)
			return true;
		return false;
	}
	
	private NodeFilter[] initialiseNodeFilterArray(){
		NodeFilter[] nfs = new NodeFilter[DEFAULT_STMT_LENGTH];
		return nfs;
	}
	
	private NodeFilter[] increaseCapacity(NodeFilter[] nfs, int newlength){
		NodeFilter[] nnfs = new NodeFilter[newlength];
		System.arraycopy(nfs, 0, nnfs, 0, nfs.length);
		return nnfs;
	}

	private NodeFilter handleFilterElement(org.w3c.dom.Node n) throws ConfigFileParseException{
		NodeList children = n.getChildNodes();
		boolean done = false;
		NodeFilter nf = null;
		
		for(int i=0; i<children.getLength(); i++){
			org.w3c.dom.Node child = children.item(i);
			if(child.getNodeName().equals("filter")){
				if(done)
					throw new ConfigFileParseException("Must have exactly one child node for filterElement.");
				nf = handleFilter(child, false);
				done = true;
			} else if(child.getNodeName().equals("notfilter")){
				if(done)
					throw new ConfigFileParseException("Must have exactly one child node for filterElement.");
				nf = handleFilter(child, true);
				done = true;
			} else if(child.getNodeName().equals("and")){
				if(done)
					throw new ConfigFileParseException("Must have exactly one child node for filterElement.");
				nf = handleCompoundFilter(child, true);
				done = true;
			} else if(child.getNodeName().equals("or")){
				if(done)
					throw new ConfigFileParseException("Must have exactly one child node for filterElement.");
				nf = handleCompoundFilter(child, false);
				done = true;
			} else if(isIgnorable(child)){
				;
			} else throw new ConfigFileParseException("Illegal child node of filterElement: "+child.getNodeName()+". Only 'filter' allowed.");
		}
		return nf;
	}
	
	private NodeFilter handleCompoundFilter(org.w3c.dom.Node n, boolean and) throws ConfigFileParseException{
		NodeList children = n.getChildNodes();
		if(children.getLength()<2){
			throw new ConfigFileParseException("Must have at least two child nodes for 'and'/'or'.");
		}
		
		ArrayList<NodeFilter> fs = new ArrayList<NodeFilter>();
		for(int i=0; i<children.getLength(); i++){
			org.w3c.dom.Node child = children.item(i);
			if(child.getNodeName().equals("filter")){
				fs.add(handleFilter(child, false));
			} else if(child.getNodeName().equals("notfilter")){
				fs.add(handleFilter(child, true));
			} else if(child.getNodeName().equals("and")){
				fs.add(handleCompoundFilter(child, true));
			} else if(child.getNodeName().equals("or")){
				fs.add(handleCompoundFilter(child, false));
			} else if(isIgnorable(child)){
				;
			} else throw new ConfigFileParseException("Illegal child node of 'and'/'or': "+child.getNodeName()+". Only 'filter', 'notFilter', 'and', 'or' allowed.");
		}

		NodeFilter[] filters = new NodeFilter[fs.size()];
		fs.toArray(filters);
		
		if(and){
			return new AndFilter(filters);
		}
		return new OrFilter(filters);
	}
	
	private AbstractFilter handleFilter(org.w3c.dom.Node n, boolean negate) throws ConfigFileParseException{
		NamedNodeMap nnm = n.getAttributes();
		if(nnm==null){
			throw new ConfigFileParseException("Missing filter attributes.");
		}
		org.w3c.dom.Node type = nnm.getNamedItem("type");
		
		if(type==null){
			throw new ConfigFileParseException("No type attribute on filter element.");
		} else{
			String t = type.getNodeValue();
			if(t.equals("regex")){
				org.w3c.dom.Node value = nnm.getNamedItem("value");
				if(value==null){
					throw new ConfigFileParseException("Missing value attribute on 'regex' filter.");
				}
				org.w3c.dom.Node flag = nnm.getNamedItem("flag");
				if(flag==null){
					return new RegexFilter(value.getNodeValue());
				}else{
					return new RegexFilter(value.getNodeValue(), flag.getNodeValue());
				}
			} else if(t.equals("equals")){
				org.w3c.dom.Node nv = nnm.getNamedItem("value");
				if(nv==null){
					throw new ConfigFileParseException("Missing value attribute on 'equals' filter.");
				}
				return new EqualsFilter(nv.getNodeValue(), negate);
			} else if(t.equals("class")){
				Class<? extends Node> nodetype = null;
				org.w3c.dom.Node nt = nnm.getNamedItem("value");
				if(nt==null){
					throw new ConfigFileParseException("Missing value attribute on 'class' filter.");
				}
				
				String nts = nt.getNodeValue();
				if(nts.equals("uri")){
					nodetype = Resource.class;
				} else if(nts.equals("bnode")){
					nodetype = BNode.class;
				} else if(nts.equals("literal")){
					nodetype = Literal.class;
				} else{
					throw new ConfigFileParseException("Unknown value for attribute 'value' of 'class' filter: "+nts+". Must be 'uri', 'bnode' or 'literal'.");
				}
				return new ClassFilter(nodetype, negate);
			} else{
				throw new ConfigFileParseException("Unknown filter type : "+t);
			}
		}
	}
	
	public static class ConfigFileParseException extends Exception{
		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;

		public ConfigFileParseException(){
			super();
		}

		public ConfigFileParseException(String msg){
			super(msg);
		}
	}
	
	public static void main(String arg[]) throws Exception{
		DOMConfigFileHandler dcfh = new DOMConfigFileHandler("test/iterator.txt");
        dcfh.parse();
        Iterator<Node[]> iter = dcfh.create();
        while(iter.hasNext()){
        	System.err.println(Nodes.toN3(iter.next()));
        }
	}
}
