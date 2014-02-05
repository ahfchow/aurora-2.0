/**
 * @(#)NodeHWCNetwork.java
 */

package aurora.hwc;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.util.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import aurora.*;


/**
 * Road Network Node.
 * @author Alex Kurzhanskiy
 * @version $Id: NodeHWCNetwork.java 146 2010-07-20 05:14:07Z akurzhan $
 */
public final class NodeHWCNetwork extends AbstractNodeComplex {
	private static final long serialVersionUID = -124608463365357280L;
	
	private AuroraInterval totalDelay = new AuroraInterval();
	private AuroraInterval totalDelaySum = new AuroraInterval();
	private boolean resetAllSums = true;
	private boolean qControl = true;
	
	protected DirectionsCache dircache = null;
	protected IntersectionCache ixcache = null;
	
	protected BufferedWriter out_writer;
	

	public NodeHWCNetwork() { }
	public NodeHWCNetwork(int id) { this.id = id; }
	public NodeHWCNetwork(int id, boolean top) {
		this.id = id;
		this.top = top;
		if (top)
			myNetwork = this;
	}
	
	
	/**
	 * Initialize OD list from the DOM structure.
	 */
	protected boolean initODListFromDOM(Node p) throws Exception {
		boolean res = true;
		if (p == null)
			return false;
		if (p.hasChildNodes()) {
			NodeList pp2 = p.getChildNodes();
			for (int j = 0; j < pp2.getLength(); j++) {
				if (pp2.item(j).getNodeName().equals("od")) {
					OD od = new ODHWC();
					od.setMyNetwork(this);
					res &= od.initFromDOM(pp2.item(j));
					addOD(od);
				}
				if (pp2.item(j).getNodeName().equals("include")) {
					Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(pp2.item(j).getAttributes().getNamedItem("uri").getNodeValue());
					if (doc.hasChildNodes())
						res &= initODListFromDOM(doc.getChildNodes().item(0));
				}
			}
		}
		return res;
	}
	
	/**
	 * Initialize signal list from the DOM structure.
	 */
	protected boolean initSignalListFromDOM(Node p) throws Exception {
		boolean res = true;
		if (p == null)
			return false;
		if (p.hasChildNodes()) {
			NodeList pp2 = p.getChildNodes();
			for (int j = 0; j < pp2.getLength(); j++) {
				if (pp2.item(j).getNodeName().equals("signal")) {
					Node attr = pp2.item(j).getAttributes().getNamedItem("node_id");
					if (attr != null) {
						AbstractNodeSimple nd = getNodeById(Integer.parseInt(attr.getNodeValue()));
						if (nd.getType() == TypesHWC.NODE_SIGNAL)
							res &= ((NodeUJSignal)nd).initSignalFromDOM(pp2.item(j));
					}
				}
				if (pp2.item(j).getNodeName().equals("include")) {
					Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(pp2.item(j).getAttributes().getNamedItem("uri").getNodeValue());
					if (doc.hasChildNodes())
						res &= initSignalListFromDOM(doc.getChildNodes().item(0));
				}
			}
		}
		return res;
	}
	
	/**
	 * Initializes Network from given DOM structure.
	 * @param p DOM node.
	 * @return <code>true</code> if operation succeeded, <code>false</code> - otherwise.
	 * @throws ExceptionConfiguration
	 */
	public boolean initFromDOM(Node p) throws ExceptionConfiguration {
		boolean do_it = !initialized;
		boolean res = super.initFromDOM(p);
		if ((res == true) && do_it) {
			Node mlc_attr = p.getAttributes().getNamedItem("ml_control");
			if (mlc_attr == null)
				mlc_attr = p.getAttributes().getNamedItem("controlled");
			Node qc_attr = p.getAttributes().getNamedItem("q_control");
			if (mlc_attr != null)
				controlled = Boolean.parseBoolean(mlc_attr.getNodeValue());
			if (qc_attr != null)
				qControl = Boolean.parseBoolean(qc_attr.getNodeValue());
		}
		if (p.hasChildNodes()) {
			NodeList pp = p.getChildNodes();
			for (int i = 0; i < pp.getLength(); i++) {
				if (p.getChildNodes().item(i).getNodeName().equals("SignalList"))
						try {
							res &= initSignalListFromDOM(p.getChildNodes().item(i));
						}
						catch(Exception e) {
							throw new ExceptionConfiguration(e.getMessage());
						}
				if (p.getChildNodes().item(i).getNodeName().equals("DirectionsCache")) {
					dircache = new DirectionsCache();
					res &= dircache.initFromDOM(p.getChildNodes().item(i));
				}
				if (p.getChildNodes().item(i).getNodeName().equals("IntersectionCache")) {
					ixcache = new IntersectionCache();
					res &= ixcache.initFromDOM(p.getChildNodes().item(i));
				}
			}
		}
		// FIXME:
		//dumpCSV();
		//readCSVFile();
		//readCSVOfframpFile();
		//readCSVOfframpFile2();
		
		String file_name = "C:/tmp/CSMP_680/betas.csv";
		File file = new File(file_name);
		
		try {

			if (!file.exists()) {
				file.createNewFile();
			}

			FileWriter fw = new FileWriter(file.getAbsoluteFile());
			out_writer = new BufferedWriter(fw);
		} catch(IOException e) {
			System.err.println("Error opening the CSV file '" + file_name + "':\n" + e.getMessage());
		}
		
		
		return res;
	}
	
	
	public BufferedWriter getOutWriter() {
		return out_writer;
	}
	
	
	private void dumpCSV() {
		String file_in = "C:/alexk/Work/I680_CSMP/DataFudging/680NB_Config.csv";
		String file_out = "C:/alexk/Work/I680_CSMP/DataFudging/I680NB_Config.csv";
		String[] ramps = {"", "/", "\\", "<"};
		
		try {
			InputStream fis = new FileInputStream(file_in);
			BufferedReader br = new BufferedReader(new InputStreamReader(fis, Charset.forName("UTF-8")));
			String buf;
			
			File file = new File(file_out);

			if (!file.exists()) {
				file.createNewFile();
			}

			FileWriter fw = new FileWriter(file.getAbsoluteFile());
			BufferedWriter bw = new BufferedWriter(fw);
			
			bw.write("\"Link ID\",\"Abs. Postmile\",\"Length (miles)\",\"GP Lanes\",\"GP Capacity (vph)\",\"Aux. Lanes\",\"Aux. Capacity (vph)\",\"HOV Lanes\",\"HOV Capacity (vph)\",\"Free Flow Speed (mph)\",\"Layout\",\"Name\"\n");
			
			while ((buf = br.readLine()) != null) {
				bw.write(buf);
				String[] blocks = buf.split(",");
				int id = Integer.parseInt(blocks[0]);
				int gp_ln = Integer.parseInt(blocks[3]);
				int aux_ln = Integer.parseInt(blocks[5]);
				int hov_ln = Integer.parseInt(blocks[7]);
				AbstractLinkHWC lnk = (AbstractLinkHWC)getLinkById(id);
				if (lnk == null) {
					System.err.println(id + "\n");
					continue;
				}
				AbstractNode bn = lnk.getBeginNode();
				AbstractNode en = lnk.getEndNode();
				String layout = "";
				String name = "";
				int ind = 0;
				if ((bn !=  null) && (bn.getSuccessors().size() > 1)) {
					ind++;
					name += bn.getName();
				}
				if ((en != null) && (en.getPredecessors().size() > 1)) {
					ind = ind + 2;
					if (ind == 3)
						name += "; ";
					name += en.getName();
				}
						
				layout = ramps[ind];
				for (int i = 0; i < aux_ln; i++)
					layout += ":";
				for (int i = 0; i < gp_ln; i++)
					layout += "|";
				for (int i = 0; i < hov_ln; i++)
					layout += "H";
				bw.write(",\"" + layout + "\",\"" + name + "\"\n");
				
			}
			
			br.close();
			br = null;
			fis = null;
			bw.close();
			bw = null;
		} catch(IOException e) {
			System.err.println("Error generating file '" + file_out + "':\n" + e.getMessage());
		}
	
		return;
	}
	
	private void readCSVFile() {
		String file_name = "C:/tmp/CSMP_680/680N_cfg.csv";
		try {
			InputStream fis = new FileInputStream(file_name);
			BufferedReader br = new BufferedReader(new InputStreamReader(fis, Charset.forName("UTF-8")));
			String buf;
			
			while ((buf = br.readLine()) != null) {
				String[] blocks = buf.split(",");
				int id = Integer.parseInt(blocks[0]);
				double cap = Double.parseDouble(blocks[1]);
				double num_lanes = cap / 1900;
				double cd = Double.parseDouble(blocks[2]);
				double jd = Double.parseDouble(blocks[3]);
				double hcap = Double.parseDouble(blocks[4]);
				double hcd = Double.parseDouble(blocks[5]);
				double hjd = Double.parseDouble(blocks[6]);
			
				AbstractLinkHWC lk = (AbstractLinkHWC)this.getLinkById(id);
				lk.setLanes(num_lanes);
				lk.setFD(cap, cd, jd, 0);
				/*if (lk.getBeginNode() != null)
					System.err.println(lk.getBeginNode().getId() + "   " + lk.getBeginNode().getSuccessors().firstElement().getTypeLetterCode());
				else
					System.err.println("0");
				*/
				if (hcap > 0) {
					AbstractNodeHWC bn = (AbstractNodeHWC)lk.getBeginNode();
					AbstractNodeHWC en = (AbstractNodeHWC)lk.getEndNode();
					LinkFwHOV hlk = new LinkFwHOV();
					hlk.setId(-2000+id);
					hlk.setMyNetwork(this);
					hlk.setLength(lk.getLength());
					hlk.setLanes(1);
					hlk.setBeginNode(bn);
					hlk.setEndNode(en);
					hlk.setFD(hcap, hcd, hjd, 0);
					addLink(hlk);
					if (bn != null)
						bn.addOutLink(hlk);
					if (en != null)
						en.addInLink(hlk);
				}
			}
			
			
			br.close();
			br = null;
			fis = null;
		} catch(IOException e) {
			System.err.println("Error reading the CSV file '" + file_name + "':\n" + e.getMessage());
		}
		
		return;
	}
	
	private void readCSVOfframpFile() {
		String file_name = "C:/tmp/CSMP_680/680N_offramps2.csv";
		String file_name2 = "C:/tmp/CSMP_680/680N_sr.xml";
		
		try {
			InputStream fis = new FileInputStream(file_name);
			BufferedReader br = new BufferedReader(new InputStreamReader(fis, Charset.forName("UTF-8")));
			String buf;
			
			File file = new File(file_name2);

			if (!file.exists()) {
				file.createNewFile();
			}

			FileWriter fw = new FileWriter(file.getAbsoluteFile());
			BufferedWriter bw = new BufferedWriter(fw);
			
			bw.write("<SplitRatioProfileSet>\n");
			
			while ((buf = br.readLine()) != null) {
				String[] blocks = buf.split(",");
				int nid = Integer.parseInt(blocks[1]);
				int lid = Integer.parseInt(blocks[2]);
				
				AbstractNodeHWC nd = (AbstractNodeHWC)this.getNodeById(nid);
				if ((nd == null) || (nd.getSuccessors().size() < 2))
					continue;
				
				bw.write("<splitratios node_id=\"" + nid + "\" start_time=\"0\" dt=\"300\">\n");
				Vector<AbstractNetworkElement> ins = nd.getPredecessors();
				Vector<AbstractNetworkElement> outs = nd.getSuccessors();
				int m = ins.size();
				int n = outs.size();
				for (int ii = 3; ii < 291; ii++) {
					bw.write("<srm>");
					for (int i = 0; i < m; i++) {
						for (int j = 0; j < n; j++) {
							AbstractLinkHWC ilk = (AbstractLinkHWC)ins.get(i);
							AbstractLinkHWC olk = (AbstractLinkHWC)outs.get(j);
							if (olk.getType() == TypesHWC.LINK_OFFRAMP) {
								if ((ilk.getType() == TypesHWC.LINK_ONRAMP) || (lid != olk.getId()))
									bw.write("0:0");
								else {
									bw.write(blocks[ii] + ":" + blocks[ii]);
								}
							} else if (olk.getType() == TypesHWC.LINK_HOV) {
								if (((ii >= 62) && (ii < 110)) || ((ii >= 182) && (ii < 230)))
									bw.write("0:-1");
								else
									bw.write("-1:-1");
							} else if (olk.getType() == TypesHWC.LINK_FREEWAY) {
								bw.write("-1:-1");
							}
							if (j < n-1)
								bw.write(", ");
						}
						if (i < m-1)
							bw.write("; ");
					}
					
					bw.write("</srm>\n");
				}
				bw.write("</splitratios>\n");
				
			}
			
			bw.write("</SplitRatioProfileSet>\n");
			br.close();
			bw.close();
			br = null;
			bw = null;
			fis = null;
		} catch(IOException e) {
			System.err.println("Error reading the CSV file '" + file_name + "':\n" + e.getMessage());
		}
		
		return;
	}
	
	private void readCSVOfframpFile2() {
		String file_name = "C:/tmp/CSMP_680/680N_offramps2.csv";
		String file_name2 = "C:/tmp/CSMP_680/680N_sr.xml";
		
		try {
			InputStream fis = new FileInputStream(file_name);
			BufferedReader br = new BufferedReader(new InputStreamReader(fis, Charset.forName("UTF-8")));
			String buf;
			
			File file = new File(file_name2);

			if (!file.exists()) {
				file.createNewFile();
			}

			FileWriter fw = new FileWriter(file.getAbsoluteFile());
			BufferedWriter bw = new BufferedWriter(fw);
			
			bw.write("<SplitRatioSet project_id=\"4\" id=\"1\">\n");
			
			int srp_id = 1;
			while ((buf = br.readLine()) != null) {
				String[] blocks = buf.split(",");
				int nid = Integer.parseInt(blocks[1]);
				int lid = Integer.parseInt(blocks[2]);
				
				AbstractNodeHWC nd = (AbstractNodeHWC)this.getNodeById(nid);
				if ((nd == null) || (nd.getSuccessors().size() < 2))
					continue;
				
				bw.write("<splitRatioProfile id=\"" + srp_id++ + "\" node_id=\"" + nid + "\" start_time=\"0\" dt=\"300\">\n");
				Vector<AbstractNetworkElement> ins = nd.getPredecessors();
				Vector<AbstractNetworkElement> outs = nd.getSuccessors();
				int m = ins.size();
				int n = outs.size();
				for (int i = 0; i < m; i++) {
					for (int j = 0; j < n; j++) {
						AbstractLinkHWC ilk = (AbstractLinkHWC)ins.get(i);
						AbstractLinkHWC olk = (AbstractLinkHWC)outs.get(j);
						bw.write("<splitratio vehicle_type_id=\"0\" link_in=\"" + ilk.getId() + "\" link_out=\"" + outs.get(j).getId() + "\">" );
						for (int ii = 3; ii < 291; ii++) {
							if (olk.getType() == TypesHWC.LINK_OFFRAMP) {
								if ((ilk.getType() == TypesHWC.LINK_ONRAMP) || (lid != olk.getId()))
									bw.write("0");
								else
									bw.write(blocks[ii]);
							} else if (olk.getType() == TypesHWC.LINK_HOV) {
								if (((ii >= 62) && (ii < 110)) || ((ii >= 182) && (ii < 230)))
									bw.write("-1");
								else
									bw.write("-1");
							} else if (olk.getType() == TypesHWC.LINK_FREEWAY) {
								bw.write("-1");
							}
							if (ii < 290)
								bw.write(",");
						}
						bw.write("</splitratio>\n");
						bw.write("<splitratio vehicle_type_id=\"1\" link_in=\"" + ilk.getId() + "\" link_out=\"" + outs.get(j).getId() + "\">" );
						for (int ii = 3; ii < 291; ii++) {
							if (olk.getType() == TypesHWC.LINK_OFFRAMP) {
								if ((ilk.getType() == TypesHWC.LINK_ONRAMP) || (lid != olk.getId()))
									bw.write("0");
								else
									bw.write(blocks[ii]);
							} else if (olk.getType() == TypesHWC.LINK_HOV) {
								if (((ii >= 62) && (ii < 110)) || ((ii >= 182) && (ii < 230)))
									bw.write("0");
								else
									bw.write("-1");
							} else if (olk.getType() == TypesHWC.LINK_FREEWAY) {
								bw.write("-1");
							}
							if (ii < 290)
								bw.write(",");
						}
						bw.write("</splitratio>\n");
					}
				}
				bw.write("</splitRatioProfile>\n");
				
			}
			
			bw.write("</SplitRatioSet>\n");
			br.close();
			bw.close();
			br = null;
			bw = null;
			fis = null;
		} catch(IOException e) {
			System.err.println("Error reading the CSV file '" + file_name + "':\n" + e.getMessage());
		}
		
		return;
	}
	
	
	/**
	 * Generates XML buffer for the initial density profile.<br>
	 * If the print stream is specified, then XML buffer is written to the stream.
	 * @param out print stream.
	 * @throws IOException
	 */
	public void xmlDumpInitialDensityProfile(PrintStream out) throws IOException {
		if (out == null)
			out = System.out;
		for (int i = 0; i < links.size(); i++)
			((AbstractLinkHWC)links.get(i)).xmlDumpInitialDensity(out);
		for (int i = 0; i < networks.size(); i++)
			((NodeHWCNetwork)networks.get(i)).xmlDumpInitialDensityProfile(out);
		return;
	}
	
	/**
	 * Generates XML buffer for the demand profile set.<br>
	 * If the print stream is specified, then XML buffer is written to the stream.
	 * @param out print stream.
	 * @throws IOException
	 */
	public void xmlDumpDemandProfileSet(PrintStream out) throws IOException {
		if (out == null)
			out = System.out;
		for (int i = 0; i < links.size(); i++)
			((AbstractLinkHWC)links.get(i)).xmlDumpDemandProfile(out);
		for (int i = 0; i < networks.size(); i++)
			((NodeHWCNetwork)networks.get(i)).xmlDumpDemandProfileSet(out);
		return;
	}
	
	/**
	 * Generates XML buffer for the capacity profile set.<br>
	 * If the print stream is specified, then XML buffer is written to the stream.
	 * @param out print stream.
	 * @throws IOException
	 */
	public void xmlDumpCapacityProfileSet(PrintStream out) throws IOException {
		if (out == null)
			out = System.out;
		for (int i = 0; i < links.size(); i++)
			((AbstractLinkHWC)links.get(i)).xmlDumpCapacityProfile(out);
		for (int i = 0; i < networks.size(); i++)
			((NodeHWCNetwork)networks.get(i)).xmlDumpCapacityProfileSet(out);
		return;
	}
	
	/**
	 * Generates XML buffer for the split ratio profile set.<br>
	 * If the print stream is specified, then XML buffer is written to the stream.
	 * @param out print stream.
	 * @throws IOException
	 */
	public void xmlDumpSplitRatioProfileSet(PrintStream out) throws IOException {
		if (out == null)
			out = System.out;
		for (int i = 0; i < nodes.size(); i++)
			((AbstractNodeHWC)nodes.get(i)).xmlDumpSplitRatioProfile(out);
		for (int i = 0; i < networks.size(); i++)
			((NodeHWCNetwork)networks.get(i)).xmlDumpSplitRatioProfileSet(out);
		return;
	}
	
	/**
	 * Generates XML buffer for the split ratio profile set.<br>
	 * If the print stream is specified, then XML buffer is written to the stream.
	 * @param out print stream.
	 * @throws IOException
	 */
	public void xmlDumpControllerSet(PrintStream out) throws IOException {
		if (out == null)
			out = System.out;
		for (int i = 0; i < controllers.size(); i++)
			controllers.get(i).xmlDump(out);
		for (int i = 0; i < nodes.size(); i++)
			((AbstractNodeHWC)nodes.get(i)).xmlDumpControllers(out);
		for (int i = 0; i < networks.size(); i++)
			((NodeHWCNetwork)networks.get(i)).xmlDumpControllerSet(out);
		return;
	}
	
	/**
	 * Generates XML buffer for the signal list.<br>
	 * If the print stream is specified, then XML buffer is written to the stream.
	 * @param out print stream.
	 * @throws IOException
	 */
	public void xmlDumpSignalList(PrintStream out) throws IOException {
		if (out == null)
			out = System.out;
		out.print("\n<SignalList>\n");
		for (int i = 0; i < nodes.size(); i++) {
			if (nodes.get(i).getType() == TypesHWC.NODE_SIGNAL)
				((NodeUJSignal)nodes.get(i)).xmlDumpSignal(out);
		}
		out.print("</SignalList>\n");
		return;
	}
	
	/**
	 * Generates XML description of the complex Node.<br>
	 * If the print stream is specified, then XML buffer is written to the stream.
	 * @param out print stream.
	 * @throws IOException
	 */
	public void xmlDump(PrintStream out) throws IOException {
		if (out == null)
			out = System.out;
		out.print("\n<network id=\"" + id + "\" name=\"" + name + "\" ml_control=\"" + controlled + "\" q_control=\"" + qControl + "\"  dt=\"" + 3600*tp + "\">\n");
		super.xmlDump(out);
		xmlDumpSignalList(out);
		if (dircache != null) {
			dircache.xmlDump(out);
		}
		if (ixcache != null) {
			ixcache.xmlDump(out);
		}
		out.print("\n</network>\n");
		return;
	}
	
	/**
	 * Updates Sensor data.<br>
	 * @param ts time step.
	 * @return <code>true</code> if all went well, <code>false</code> - otherwise.
	 * @throws ExceptionDatabase, ExceptionSimulation
	 */
	public synchronized boolean sensorDataUpdate(int ts) throws ExceptionDatabase, ExceptionSimulation {
		if (resetAllSums)
			resetSums();
		int initTS = Math.max(myNetwork.getContainer().getMySettings().getTSInitial(), (int)(myNetwork.getContainer().getMySettings().getTimeInitial()/myNetwork.getTop().getTP()));
		if ((ts - initTS == 1) || (((ts - tsV) * getTop().getTP()) >= container.getMySettings().getDisplayTP()))
			resetAllSums = true;
		if (ts - initTS == 1)
			resetSums();
		return super.sensorDataUpdate(ts);
	}
	
	/**
	 * Updates Link data.<br>
	 * @param ts time step.
	 * @return <code>true</code> if all went well, <code>false</code> - otherwise.
	 * @throws ExceptionDatabase, ExceptionSimulation
	 */
	public synchronized boolean linkDataUpdate(int ts) throws ExceptionDatabase, ExceptionSimulation {
		totalDelay.setCenter(0, 0);
		boolean res = super.linkDataUpdate(ts);
		totalDelaySum.add(totalDelay);
		if (!isTop())
			((NodeHWCNetwork)myNetwork).addToTotalDelay(totalDelay);
		return res;
	}
	
	/**
	 * Validates Network configuration.<br>
	 * Initiates validation of all Monitors, Nodes and Links that belong to this node.
	 * @return <code>true</code> if configuration is correct, <code>false</code> - otherwise.
	 * @throws ExceptionConfiguration
	 */
	public boolean validate() throws ExceptionConfiguration {
		boolean res = super.validate();
		return res;
	}
	
	/**
	 * Returns <code>true</code> if queue control is on, <code>false</code> otherwise.
	 */
	public boolean hasQControl() {
		return qControl;
	}
	
	/**
	 * Returns type.
	 */
	public final int getType() {
		return TypesHWC.NETWORK_HWC;
	}
	
	/**
	 * Returns letter code of the Node type.
	 */
	public String getTypeLetterCode() {
		return "N";
	}
	
	/**
	 * Returns type description.
	 */
	public final String getTypeString() {
		return "Network";
	}
	
	/**
	 * Returns total network delay.
	 */
	public final AuroraInterval getDelay() {
		AuroraInterval v = new AuroraInterval();
		v.copy(totalDelay);
		return v;
	}
	
	/**
	 * Returns sum of total network delay.
	 */
	public final AuroraInterval getSumDelay() {
		AuroraInterval v = new AuroraInterval();
		v.copy(totalDelaySum);
		return v;
	}
	
	/**
	 * Sets mainline control mode On/Off and queue control On/Off.<br>
	 * @param cv true/false value for mainline control.
	 * @param qcv true/false value for queue control.
	 * @return <code>true</code> if operation succeeded, <code>false</code> - otherwise.
	 */
	public synchronized boolean setControlled(boolean cv, boolean qcv) {
		boolean res = true;
		controlled = cv;
		qControl = qcv;
		for (int i = 0; i < networks.size(); i++) {
			res &= ((NodeHWCNetwork)networks.get(i)).setControlled(cv, qcv);
		}
		return res;
	}
	
	/**
	 * Increments total delay by the given value.
	 */
	public synchronized void addToTotalDelay(AuroraInterval x) {
		totalDelay.add(x);
		return;
	}
	
	/**
	 * Resets quantities derived by integration: VHT, VMT, Delay, Productivity Loss.
	 */
	public synchronized void resetSums() {
		totalDelaySum.setCenter(0, 0);
		resetAllSums = false;
		return;
	}
	
	/**
	 * Adjust vector data according to new vehicle weights.
	 * @param w array of weights.
	 * @return <code>true</code> if successful, <code>false</code> - otherwise.
	 */
	public synchronized boolean adjustWeightedData(double[] w) {
		int i;
		boolean res = true;
		for (i = 0; i < networks.size(); i++)
			res &= ((NodeHWCNetwork)networks.get(i)).adjustWeightedData(w);
		for (i = 0; i < nodes.size(); i++)
			res &= ((AbstractNodeHWC)nodes.get(i)).adjustWeightedData(w);
		for (i = 0; i < links.size(); i++)
			res &= ((AbstractLinkHWC)links.get(i)).adjustWeightedData(w);
		return res;
	}
	
	/**
	 * Returns density over multiple links specified in the given vector.
	 * @param v vector of links
	 * @return density vector
	 */
	public AuroraIntervalVector computeMultiLinkDensity(Vector<AbstractLinkHWC> v) {
		if ((v == null) || (v.size() == 0))
			return null;
		AuroraIntervalVector den = new AuroraIntervalVector();
		den.copy(v.firstElement().getDensity());
		double len = v.firstElement().getLength();
		den.affineTransform(len, 0);
		for (int i = 1; i < v.size(); i++) {
			double ll = v.get(i).getLength();
			len += ll;
			AuroraIntervalVector dd = v.get(i).getDensity();
			dd.affineTransform(ll, 0);
			den.add(dd);
		}
		den.affineTransform(1/len, 0);
		return den;
	}
	
	/**
	 * Returns critical density over multiple links specified in the given vector.
	 * @param v vector of links
	 * @return critical density
	 */
	public double computeMultiLinkCriticalDensity(Vector<AbstractLinkHWC> v) {
		if ((v == null) || (v.size() == 0))
			return 0;
		double len = v.firstElement().getLength();
		double den_crit = v.firstElement().getCriticalDensity() * len;
		for (int i = 1; i < v.size(); i++) {
			double ll = v.get(i).getLength();
			len += ll;
			double cd = v.get(i).getCriticalDensity() * ll;
			den_crit += cd;
		}
		den_crit = den_crit / len;
		return den_crit;
	}
	
}
