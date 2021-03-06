/**
 * @(#)NodeUJSignal.java
 */

package aurora.hwc;

import java.io.*;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import aurora.*;
import aurora.hwc.control.signal.*;

/**
 * Urban Junction with Signal.
 * <br>Allowed input links (predecessors): LinkStreet, LinkFR, LinkDummy.
 * <br>Allowed output links (successors): LinkStreet, LinkOR, LinkDummy.
 * 
 * @see LinkStreet, LinkOR, LinkFR, LinkDummy
 *  
 * @author Alex Kurzhanskiy, Gabriel Gomes
 * @version $Id: NodeUJSignal.java 135 2010-06-05 00:57:31Z akurzhan $
 */
public final class NodeUJSignal extends AbstractNodeHWC {
	private static final long serialVersionUID = 8638143194434976281L;

	private boolean secondpass = false;
	private SignalManager sigman = new SignalManager(this);;
	public boolean hasdata = false;

	
	public NodeUJSignal() { }		
	public NodeUJSignal(int id) { 
		this.id = id; 
	}

	
	public SignalManager getSigMan() { return sigman; };

	public synchronized boolean dataUpdate(int ts) throws ExceptionDatabase, ExceptionSimulation {
		boolean res = sigman.Update();
		res &= super.dataUpdate(ts);		
		return res;
	}
	
	/**
	 * Initializes the signalized intersection Node from given DOM structure.
	 * @param p DOM node.
	 * @return <code>true</code> if operation succeeded, <code>false</code> - otherwise.
	 * @throws ExceptionConfiguration
	 */
	public boolean initSignalFromDOM(Node p) throws ExceptionConfiguration {
		boolean res = true;
		if (p == null)
			return false;
		hasdata = true;
		NodeList pp = p.getChildNodes();
		for (int i = 0; i < pp.getLength(); i++) {
			if (pp.item(i).getNodeName().equals("phase")) {
				Node nemaAttr = pp.item(i).getAttributes().getNamedItem("nema");
				if (nemaAttr != null) {
					int nema_idx = NEMAtoIndex(Integer.parseInt(nemaAttr.getNodeValue()));
					if (nema_idx < 0)
						continue;
					res &= sigman.Phase(nema_idx).initFromDOM(pp.item(i));
				}
			}
		}	
		return res;
	}
	
	/**
	 * Initializes the signalized intersection Node from given DOM structure.
	 * @param p DOM node.
	 * @return <code>true</code> if operation succeeded, <code>false</code> - otherwise.
	 * @throws ExceptionConfiguration
	 */
	public boolean initFromDOM(Node p) throws ExceptionConfiguration {
		boolean res = super.initFromDOM(p);
		if (secondpass)	{
			if (p.hasChildNodes()) {
				NodeList pp = p.getChildNodes();
				for (int i = 0; i < pp.getLength(); i++) {
					if (pp.item(i).getNodeName().equals("signal"))
						res &= initSignalFromDOM(pp.item(i));
				}
			}
			else
				res = false;
		}	// secondpass
		else
			secondpass = true;
		return res;
	}
	
	/**
	 * Generates XML description of the NodeUJSignal.<br>
	 * If the print stream is specified, then XML buffer is written to the stream.
	 * @param out print stream.
	 * @throws IOException
	 */
	public void xmlDumpSignal(PrintStream out) throws IOException {
		if (out == null)
			out = System.out;
		if (hasdata) {
			out.print("<signal node_id=\"" + id + "\">\n");
			for (int i = 0; i < 8; i++) {	
				sigman.Phase(i).xmlDump(out);
			}
			out.print("</signal>\n");
		}
		return;
	}
	
	/**
	 * Validates Node configuration.<br>
	 * Checks that in- and out-links are of correct types.
	 * @return <code>true</code> if configuration is correct, <code>false</code> - otherwise.
	 * @throws ConfigurationException
	 */
	public boolean validate() throws ExceptionConfiguration {
		boolean res = super.validate();
		int type;
		String cnm;
		for (int i = 0; i < predecessors.size(); i++) {
			type = predecessors.get(i).getType();
			cnm = predecessors.get(i).getClass().getName();
			if ((type != TypesHWC.LINK_STREET) &&
				(type != TypesHWC.LINK_OFFRAMP) &&
				(type != TypesHWC.LINK_HIGHWAY) &&
				(type != TypesHWC.LINK_DUMMY)) {
				myNetwork.addConfigurationError(new ErrorConfiguration(this, "In-Link of wrong type (" + cnm + ")."));
				res = false;
			}
		}
		for (int i = 0; i < successors.size(); i++) {
			type = successors.get(i).getType();
			cnm = successors.get(i).getClass().getName();
			if ((type != TypesHWC.LINK_STREET) &&
				(type != TypesHWC.LINK_ONRAMP) &&
				(type != TypesHWC.LINK_HIGHWAY) &&
				(type != TypesHWC.LINK_DUMMY)) {
				myNetwork.addConfigurationError(new ErrorConfiguration(this, "Out-Link of wrong type (" + cnm + ")."));
				res = false;
			}
		}
		
		// Connect detector stations to sensors
		for(int i = 0; i < 8; i++){
			if(sigman.Phase(i).ApproachStation()!=null)
				res &= sigman.Phase(i).ApproachStation().AssignLoops(sigman.Phase(i).ApproachStationIds);
			if(sigman.Phase(i).StoplineStation()!=null)
				res &= sigman.Phase(i).StoplineStation().AssignLoops(sigman.Phase(i).StoplineStationIds);
		}
		
		return res;
	}
	

	/**
	 * Additional initialization.
	 * @return <code>true</code> if operation succeeded, <code>false</code> - otherwise.
	 * @throws ExceptionConfiguration, ExceptionDatabase
	 * @throws ExceptionDatabase 
	 */
	public boolean initialize() throws ExceptionConfiguration, ExceptionDatabase {
		boolean res = super.initialize();
		sigman.initialize();
/*		int i;
		for(i=0;i<8;i++){
			if(sigman.Phase(i).link!=null){
				sigman.attachSimpleController( this , sigman.Phase(i).link);	
			}
		}*/
		return res;
	}
	
	private int NEMAtoIndex(int nema){
		if(nema<1 || nema > 8)
			return -1;
		else
			return nema-1;
	}
	
	/**
	 * Returns type.
	 */
	public final int getType() {
		return TypesHWC.NODE_SIGNAL;
	}
	
	/**
	 * Returns letter code of the Node type.
	 */
	public String getTypeLetterCode() {
		return "S";
	}
	
	/**
	 * Returns type description.
	 */
	public final String getTypeString() {
		return "Signal Intersection";
	}
	
	/**
	 * Returns compatible simple controller type names.
	 */
	public String[] getSimpleControllerTypes() {
		String[] ctrlTypes = {"Simple Signal"};
		return ctrlTypes;
	}
	
	/**
	 * Returns compatible simple controller classes.
	 */
	public String[] getSimpleControllerClasses() {
		String[] ctrlClasses = {"aurora.hwc.control.ControllerSimpleSignal"};
		return ctrlClasses;
	}
	
	
	/*
	public class Phase  {
		private int NEMAid;
		private Vector<AbstractLink> mylinks = new Vector<AbstractLink>();
		private NodeUJSignal signalnode;
		
		public Phase(int nema, NodeUJSignal s){
			NEMAid = nema;
			signalnode = s;
		}
		public void addLink(AbstractLink L){
			mylinks.add(L);
		}
		public int getNEMA() { 
			return NEMAid; 
			};
		public int getNumLinks() { 
			return mylinks.size();
			};
		public AbstractLink getLink(int i) { 
			return mylinks.get(i); 
			};
		public NodeUJSignal getSignalNode(){ 
			return signalnode; 
		};
	}
	*/

}