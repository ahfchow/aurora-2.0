/**
 * @(#)AbstractLinkHWC.java
 */

package aurora.hwc;

import java.io.*;
import java.text.NumberFormat;
import java.util.*;

import org.w3c.dom.*;

import aurora.*;
import aurora.util.*;


/**
 * Base class for all links in a highway network.
 * 
 * @see LinkFwML, LinkFwHOV, LinkHw, LinkOR, LinkFR, LinkIc, LinkStreet, LinkDummy
 * 
 * @author Alex Kurzhanskiy
 * @version $Id: AbstractLinkHWC.java 157 2011-03-30 04:30:49Z akurzhan $
 */
public abstract class AbstractLinkHWC extends AbstractLink {
	private static final long serialVersionUID = 7448808131935808045L;
	
	protected String name = null;
	
	protected double lanes = 1.0;
	protected double flowMax = 1800; // in vph
	protected AuroraInterval flowMaxRange = new AuroraInterval(flowMax, 0);
	protected AuroraInterval currentWeavingFactor = new AuroraInterval(1, 0);
	protected AuroraInterval inputWeavingFactor = new AuroraInterval(1, 0);
	protected boolean wfUpperBoundFirst = false;
	protected boolean iwfUpperBoundFirst = false;
	protected double[] outputWeavingFactors = null;
	protected double capacityDrop = 0; // in vph
	protected double densityCritical = 30; // in vpm
	protected double densityJam = 150; // in vpm
	protected boolean inUpperBoundFirst = false;
	protected boolean outUpperBoundFirst = false;
	
	//protected Vector<Double> densityH = new Vector<Double>();
	//protected Vector<Double> oflowH = new Vector<Double>();
	
	protected int tsV = 0;
	protected int tsCount = 0;
	protected boolean resetAllSums = true;
	
	protected AuroraIntervalVector density0 = new AuroraIntervalVector(); // initial value in vpm
	protected AuroraIntervalVector density = new AuroraIntervalVector(); // in vpm
	protected AuroraIntervalVector densitySum = new AuroraIntervalVector(); // in vpm
	protected AuroraIntervalVector inflowSum = new AuroraIntervalVector(); // in vph
	protected AuroraIntervalVector outflowSum = new AuroraIntervalVector(); // in vph
	protected AuroraInterval speed = new AuroraInterval(); // mean speed 
	protected AuroraInterval speedSum = new AuroraInterval(); // mean speed 
	protected AuroraInterval vht = new AuroraInterval(); // vehicle hours traveled per sampling time period
	protected AuroraInterval vhtSum = new AuroraInterval();
	protected AuroraInterval vmt = new AuroraInterval(); // vehicle miles traveled per sampling time period
	protected AuroraInterval vmtSum = new AuroraInterval();
	protected AuroraInterval delay = new AuroraInterval(); // delay introduced when speed is below free flow speed
	protected AuroraInterval delaySum = new AuroraInterval();
	protected AuroraInterval ploss = new AuroraInterval(); // productivity loss
	protected AuroraInterval plossSum = new AuroraInterval();
	
	protected double qMax = 100; // max queue size in vehicles
	protected AuroraIntervalVector qSize = new AuroraIntervalVector(); // actual queue size in vehicles
	
	protected Vector<AuroraIntervalVector> demand = new Vector<AuroraIntervalVector>(); // demand profile (values in vph)
	protected Vector<AuroraIntervalVector> procDemand = new Vector<AuroraIntervalVector>(); // demand profile (values in vph)
	protected double demandTP = 1.0/12.0; // demand value change period (default: 1/12 hour)
	protected double demandST = 0.0; // start time for demand profile
	protected double[] demandKnobs = new double[1]; //coefficients by which the demand values must be multiplied
	protected AuroraIntervalVector extDemandVal = null; // demand value that can be set externally, say, by a monitor
	
	protected Vector<AuroraInterval> capacity = new Vector<AuroraInterval>(); // capacity profile (values in vph)
	protected double capacityTP = 1.0/12.0;  // capacity value change period (default: 1/12 hour)
	protected double capacityST = 0.0; // start time for capacity profile
	protected AuroraInterval extCapVal = null; // capacity value that can be set externally, say, by a monitor
	
	protected DynamicsHWC myDynamics = new DynamicsCTM();
	
	protected AbstractNodeHWC terminalNode = null; // terminal node for open-ended link
	
	
	/**
	 * Initializes the Link from given DOM structure.
	 * @param p DOM node.
	 * @return <code>true</code> if operation succeeded, <code>false</code> - otherwise.
	 * @throws ExceptionConfiguration
	 */
	public boolean initFromDOM(Node p) throws ExceptionConfiguration {
		boolean res = true;
		if ((p == null) || (myNetwork == null))
			return !res;
		try  {
			id = Integer.parseInt(p.getAttributes().getNamedItem("id").getNodeValue());
			length = Double.parseDouble(p.getAttributes().getNamedItem("length").getNodeValue());
			if (length > 5)
				length = 0.000189393939 * length; // feet to miles
			if (p.getAttributes().getNamedItem("name") != null) 
				name = p.getAttributes().getNamedItem("name").getNodeValue();
			lanes = Double.parseDouble(p.getAttributes().getNamedItem("lanes").getNodeValue());
			Node sa = p.getAttributes().getNamedItem("record");
			if ((sa != null) && Boolean.parseBoolean(sa.getNodeValue()))
				saveState = 3;
			if (lanes < 1.0)
				lanes = 1.0;
			density0 = new AuroraIntervalVector(((SimulationSettingsHWC)myNetwork.getContainer().getMySettings()).countVehicleTypes());
			density = new AuroraIntervalVector(((SimulationSettingsHWC)myNetwork.getContainer().getMySettings()).countVehicleTypes());
			if (p.hasChildNodes()) {
				NodeList pp = p.getChildNodes();
				for (int i = 0; i < pp.getLength(); i++) {
					if (pp.item(i).getNodeName().equals("begin")) {
						Node bid_attr = pp.item(i).getAttributes().getNamedItem("node_id");
						if (bid_attr == null)
							bid_attr = pp.item(i).getAttributes().getNamedItem("id");
						AbstractNode bnd = myNetwork.getNodeById(Integer.parseInt(bid_attr.getNodeValue()));
						if (bnd != null)
							bnd.addOutLink(this);
						if (bnd.getType() == TypesHWC.NODE_TERMINAL) {
							terminalNode = (AbstractNodeHWC)bnd;
							myPosition = new PositionLink();
							myPosition.setBegin(terminalNode.getPosition().get());
							myNetwork.deleteNode(terminalNode);
						}
						else
							setBeginNode(bnd);
					}
					if (pp.item(i).getNodeName().equals("end")) {
						Node eid_attr = pp.item(i).getAttributes().getNamedItem("node_id");
						if (eid_attr == null)
							eid_attr = pp.item(i).getAttributes().getNamedItem("id");
						AbstractNode end = myNetwork.getNodeById(Integer.parseInt(eid_attr.getNodeValue()));
						if (end != null)
							end.addInLink(this);
						if (end.getType() == TypesHWC.NODE_TERMINAL) {
							terminalNode = (AbstractNodeHWC)end;
							myPosition = new PositionLink();
							myPosition.setEnd(terminalNode.getPosition().get());
							myNetwork.deleteNode(terminalNode);
						}
						else
							setEndNode(end);
					}
					if (pp.item(i).getNodeName().equals("dynamics")) {
						Node type_attr = pp.item(i).getAttributes().getNamedItem("type");
						String class_name = null;
						if (type_attr != null)
							class_name = myNetwork.getContainer().mdlType2Classname(type_attr.getNodeValue());
						else
							class_name = pp.item(i).getAttributes().getNamedItem("class").getNodeValue();
						Class c = Class.forName(class_name);
						myDynamics = (DynamicsHWC)c.newInstance();
					}
					if (pp.item(i).getNodeName().equals("density")) {
						setInitialDensity(pp.item(i).getTextContent());
					}
					if (pp.item(i).getNodeName().equals("demand")) {
						demandTP = Double.parseDouble(pp.item(i).getAttributes().getNamedItem("tp").getNodeValue());
						if (demandTP > 24) // sampling period in seconds
							demandTP = demandTP/3600;
						setDemandKnobs(pp.item(i).getAttributes().getNamedItem("knob").getNodeValue());
						setDemandVector(pp.item(i).getTextContent());
					}
					if (pp.item(i).getNodeName().equals("capacity")) {
						capacityTP = Double.parseDouble(pp.item(i).getAttributes().getNamedItem("tp").getNodeValue());
						if (capacityTP > 24) // sampling period in seconds
							capacityTP = capacityTP/3600;
						setCapacityVector(pp.item(i).getTextContent());
					}
					if (pp.item(i).getNodeName().equals("qmax"))
						qMax = Double.parseDouble(pp.item(i).getTextContent());
					if (pp.item(i).getNodeName().equals("fd")) {
						flowMaxRange.setIntervalFromString(pp.item(i).getAttributes().getNamedItem("flowMax").getNodeValue());
						double cd = Double.parseDouble(pp.item(i).getAttributes().getNamedItem("densityCritical").getNodeValue());
						double jd = Double.parseDouble(pp.item(i).getAttributes().getNamedItem("densityJam").getNodeValue());
						flowMaxRange.affineTransform(lanes, 0);
						cd = cd * lanes;
						jd = jd * lanes;
						if (!setFD(flowMaxRange.getCenter(), cd, jd))
							defaultFD();
						if ((myNetwork.getContainer().isSimulation()) && !myNetwork.getContainer().getMySettings().isPrediction())
							randomizeFD();
						Node cdp = pp.item(i).getAttributes().getNamedItem("capacityDrop");
						if (cdp != null)
							capacityDrop = Math.max(0, Math.min(flowMax, lanes*Double.parseDouble(cdp.getNodeValue())));
					}
					if (pp.item(i).getNodeName().equals("position")) {
						myPosition = new PositionLink();
						res &= myPosition.initFromDOM(pp.item(i));
					}
				}
			}
			else
				res = false;
		}
		catch(Exception e) {
			res = false;
			throw new ExceptionConfiguration(e.getMessage());
		}
		if (name == null) {
			name = ">";
			AbstractNode bnd = getBeginNode();
			AbstractNode end = getEndNode();
			if (bnd != null) 
				name = bnd.getName() + " >";
			if (end != null) 
				name += " " + end.getName();
		}
		initialized = true;
		return res;
	}
	
	/**
	 * Generates XML buffer for the terminal node if applicable.<br>
	 * If the print stream is specified, then XML buffer is written to the stream.
	 * @param out print stream.
	 * @throws IOException
	 */
	public void xmlDumpTerminalNode(PrintStream out) throws IOException {
		if (out == null)
			out = System.out;
		if (terminalNode != null) {
			terminalNode.xmlDump(out);
			return;
		}
		if (getBeginNode() == null) {
			NodeTerminal ndt = new NodeTerminal(id);
			ndt.addOutLink(this);
			ndt.getPosition().set(getPosition().get().firstElement());
			ndt.xmlDump(out);
			return;
		}
		if (getEndNode() == null) {
			NodeTerminal ndt = new NodeTerminal(id);
			ndt.addInLink(this);
			ndt.getPosition().set(getPosition().get().lastElement());
			ndt.xmlDump(out);
		}
		return;
	}
	
	/**
	 * Generates XML buffer for the initial density.<br>
	 * If the print stream is specified, then XML buffer is written to the stream.
	 * @param out print stream.
	 * @throws IOException
	 */
	public void xmlDumpInitialDensity(PrintStream out) throws IOException {
		if (out == null)
			out = System.out;
		out.print("<density link_id=\"" + id + "\">");
		out.print(density.toStringWithInverseWeights(((SimulationSettingsHWC)myNetwork.getContainer().getMySettings()).getVehicleWeights(), false));
		out.print("</density>\n");
		return;
	}
	
	/**
	 * Generates XML buffer for the demand profile.<br>
	 * If the print stream is specified, then XML buffer is written to the stream.
	 * @param out print stream.
	 * @throws IOException
	 */
	public void xmlDumpDemandProfile(PrintStream out) throws IOException {
		if (out == null)
			out = System.out;
		if ((getBeginNode() == null) && (!demand.isEmpty())) {
			out.print("<demand link_id=\"" + id + "\" start_time=\"" + Math.round(3600*demandST) + "\" dt=\"" + Math.round(3600*demandTP) + "\" knob=\"" + getDemandKnobsAsString() + "\">");
			out.print(getDemandVectorAsString());
			out.println("</demand>");
		}
		return;
	}
	
	/**
	 * Generates XML buffer for the capacity profile.<br>
	 * If the print stream is specified, then XML buffer is written to the stream.
	 * @param out print stream.
	 * @throws IOException
	 */
	public void xmlDumpCapacityProfile(PrintStream out) throws IOException {
		if (out == null)
			out = System.out;
		if ((getEndNode() == null) && (!capacity.isEmpty())) {
			out.print("<capacity link_id=\"" + id + "\" start_time=\"" + Math.round(3600*capacityST) + "\" dt=\"" + Math.round(3600*capacityTP) + "\">");
			out.print(getCapacityVectorAsString());
			out.println("</capacity>");
		}
		return;
	}
	
	/**
	 * Generates XML description of the Link.<br>
	 * If the print stream is specified, then XML buffer is written to the stream.
	 * @param out print stream.
	 * @throws IOException
	 */
	public void xmlDump(PrintStream out) throws IOException {
		if (out == null)
			out = System.out;
		boolean ss = false;
		if ((saveState == 3) || (saveState == 2))
			ss = true;
		out.print("<link type=\"" + getTypeLetterCode() + "\" id=\"" + Integer.toString(id) + "\" name=\"" + name + "\" length=\"" + Math.round(5280*length) + "\" lanes=\"" + Double.toString(lanes) + "\" record=\"" + ss + "\">\n");
		AbstractNode bn = getBeginNode();
		int bid = id;
		if (bn != null)
			bid = bn.getId();
		else if (terminalNode != null)
			bid = terminalNode.getId();
		out.print("  <begin node_id=\"" + bid + "\"/>\n");
		AbstractNode en = getEndNode();
		int eid = id;
		if (en != null)
			eid = en.getId();
		else if (terminalNode != null)
			eid = terminalNode.getId();
		out.print("  <end node_id=\"" + eid + "\"/>\n");
		out.print("  <dynamics type=\"" + myDynamics.getTypeLetterCode() + "\"/>\n");
		out.print("  <qmax>" + Double.toString(qMax) + "</qmax>\n");
		AuroraInterval fmr = new AuroraInterval(flowMaxRange.getCenter(), flowMaxRange.getSize());
		fmr.affineTransform(1/lanes, 0);
		out.print("  <fd densityCritical =\"" + Double.toString(densityCritical/lanes) + "\" densityJam=\"" + Double.toString(densityJam/lanes) + "\" flowMax=\"" + fmr.toString() + "\" capacityDrop=\"" + Double.toString(capacityDrop/lanes) + "\"/>\n");
		//out.print("  <fd densityCritical =\"" + Double.toString(densityCritical/lanes) + "\" densityJam=\"" + Double.toString(densityJam/lanes) + "\" flowMax=\"" + Double.toString(flowMaxRange.getCenter()/lanes) + "\" capacityDrop=\"" + Double.toString(capacityDrop/lanes) + "\"/>\n");
		out.print("</link>\n");
		return;
	}

	/**
	 * Updates Link data.<br>
	 * @param ts time step.
	 * @return <code>true</code> if all went well, <code>false</code> - otherwise.
	 * @throws ExceptionDatabase, ExceptionSimulation
	 */
	public synchronized boolean dataUpdate(int ts) throws ExceptionDatabase, ExceptionSimulation {
		boolean res = super.dataUpdate(ts);
		if (res) {
			if (resetAllSums)
				resetSums();
			PrintStream os = null;
			int initTS = Math.max(myNetwork.getContainer().getMySettings().getTSInitial(), (int)(myNetwork.getContainer().getMySettings().getTimeInitial()/myNetwork.getTop().getTP()));
			if ((ts - initTS == 1) || (((ts - tsV) * myNetwork.getTop().getTP()) >= myNetwork.getTop().getContainer().getMySettings().getDisplayTP())) {
				tsV = ts;
				resetAllSums = true;
				if ((saveState == 3) || (myNetwork.getContainer().isBatch()))
					os = myNetwork.getContainer().getMySettings().getTmpDataOutput();
			}
			tsCount++;

			if (predecessors.size() == 0) {
				AuroraIntervalVector ofl = getActualFlow();
				double tp = myNetwork.getTP();
				qSize.affineTransform(1/tp, 0);
				qSize.add((AuroraIntervalVector)getDemand());
				for (int i = 0; i < qSize.size(); i++) {
					if (outUpperBoundFirst)
						qSize.get(i).setBounds(qSize.get(i).getLowerBound()-ofl.get(i).getUpperBound(), qSize.get(i).getUpperBound()-ofl.get(i).getLowerBound());
					else
						qSize.get(i).setBounds(qSize.get(i).getLowerBound()-ofl.get(i).getLowerBound(), qSize.get(i).getUpperBound()-ofl.get(i).getUpperBound());
				}
				qSize.affineTransform(tp, 0);
				qSize.constraintLB(0);
				if (extDemandVal != null) {
					inflowSum.add(extDemandVal);
					qSize.affineTransform(0, 0);
				}
				else
					inflowSum.add(getDemand());
			}
			else {
				qSize.set(new AuroraInterval());
				AuroraIntervalVector iflV = (AuroraIntervalVector)getBeginNode().getOutputs().get(getBeginNode().getSuccessors().indexOf(this));
				for (int i = 0; i < inflowSum.size(); i++) {
					if (inflowSum.get(i).isInverted()) {
						if (inUpperBoundFirst)
							inflowSum.get(i).setBounds(inflowSum.get(i).getUpperBound()+iflV.get(i).getUpperBound(), inflowSum.get(i).getLowerBound()+iflV.get(i).getLowerBound());
						else
							inflowSum.get(i).setBounds(inflowSum.get(i).getUpperBound()+iflV.get(i).getLowerBound(), inflowSum.get(i).getLowerBound()+iflV.get(i).getUpperBound());
					}
					else {
						if (inUpperBoundFirst)
							inflowSum.get(i).setBounds(inflowSum.get(i).getLowerBound()+iflV.get(i).getUpperBound(), inflowSum.get(i).getUpperBound()+iflV.get(i).getLowerBound());
						else
							inflowSum.get(i).setBounds(inflowSum.get(i).getLowerBound()+iflV.get(i).getLowerBound(), inflowSum.get(i).getUpperBound()+iflV.get(i).getUpperBound());
					}
				}
			}
			AuroraIntervalVector oflV = getActualFlow();
			for (int i = 0; i < outflowSum.size(); i++) {
				if (outflowSum.get(i).isInverted()) {
					if (outUpperBoundFirst)
						outflowSum.get(i).setBounds(outflowSum.get(i).getUpperBound()+oflV.get(i).getUpperBound(), outflowSum.get(i).getLowerBound()+oflV.get(i).getLowerBound());
					else
						outflowSum.get(i).setBounds(outflowSum.get(i).getUpperBound()+oflV.get(i).getLowerBound(), outflowSum.get(i).getLowerBound()+oflV.get(i).getUpperBound());
				}
				else {
					if (outUpperBoundFirst)
						outflowSum.get(i).setBounds(outflowSum.get(i).getLowerBound()+oflV.get(i).getUpperBound(), outflowSum.get(i).getUpperBound()+oflV.get(i).getLowerBound());
					else
						outflowSum.get(i).setBounds(outflowSum.get(i).getLowerBound()+oflV.get(i).getLowerBound(), outflowSum.get(i).getUpperBound()+oflV.get(i).getUpperBound());
				}
			}
			for (int i = 0; i < qSize.size(); i++)  { // make sure queue has no negative values
				if (qSize.get(i).getUpperBound() < 0.0)
					qSize.get(i).setCenter(0.0, 0.0);
				else
					qSize.get(i).setBounds(Math.max(qSize.get(i).getLowerBound(), 0.0), qSize.get(i).getUpperBound());
			}
			speed.copy((AuroraInterval)myDynamics.computeSpeed(this));
			speedSum.add(speed);			
			density = (AuroraIntervalVector)myDynamics.computeDensity(this);	
			densitySum.add(density);
			if (os != null) {
				NumberFormat form = NumberFormat.getInstance();
				form.setMinimumFractionDigits(0);
				form.setMaximumFractionDigits(2);
				form.setGroupingUsed(false);
				os.print(", " + getAverageDensity().toString3() + ";"
						+ getAverageInFlow().toString3() + ";"
						+ getAverageOutFlow().toString3() + ";"
						+ form.format(flowMax) + ":" + form.format(densityCritical) + ":" + form.format(densityJam) + ";"
						+ form.format(qMax) + ";"
						+ currentWeavingFactor.toString3());
			}
			double tp = myNetwork.getTP();
			if (predecessors.size() == 0) {
				vht.copy(qSize.sum());
				vht.affineTransform(tp, 0);
			}
			else {
				vht.copy(density.sum());
				vht.affineTransform(length, 0);
				vht.affineTransform(tp, 0);
			}
			vhtSum.add(vht);
			if (getBeginNode() == null) {
				vmt.copy(getActualFlow().sum());
				vmt.affineTransform(length, 0);
				vmt.affineTransform(tp, 0);
				vmt.setCenter(0, 0);
			}
			else {
				vmt.setBounds(density.sum().getLowerBound()*speed.getUpperBound()*length*tp, density.sum().getUpperBound()*speed.getLowerBound()*length*tp);
			}
			if (vmtSum.isInverted()) {
				if (vmt.isInverted())
					vmtSum.setBounds(vmtSum.getUpperBound()+vmt.getUpperBound(), vmtSum.getLowerBound()+vmt.getLowerBound());
				else
					vmtSum.setBounds(vmtSum.getUpperBound()+vmt.getLowerBound(), vmtSum.getLowerBound()+vmt.getUpperBound());
			}
			else {
				if (vmt.isInverted())
					vmtSum.setBounds(vmtSum.getLowerBound()+vmt.getUpperBound(), vmtSum.getUpperBound()+vmt.getLowerBound());
				else
					vmtSum.setBounds(vmtSum.getLowerBound()+vmt.getLowerBound(), vmtSum.getUpperBound()+vmt.getUpperBound());
			}
			delay.copy(vmt);
			delay.affineTransform(1/getV(), 0);
			double dlb, dub;
			if (vmt.isInverted()) {
				dlb = delay.getUpperBound();
				dub = delay.getLowerBound();
			} 
			else {
				dlb = delay.getLowerBound();
				dub = delay.getUpperBound();
			}
			delay.setBounds(vht.getLowerBound()-dlb, vht.getUpperBound()-dub);
			delay.constraintLB(0);
			delaySum.add(delay);
			((NodeHWCNetwork)myNetwork).addToTotalDelay(delay);
			if (density.sum().getCenter() <= densityCritical)
				ploss.setCenter(0, 0);
			else {
				ploss.setBounds(getActualFlow().sum().getLowerBound()/flowMaxRange.getLowerBound(), getActualFlow().sum().getUpperBound()/flowMaxRange.getUpperBound());
				ploss.negative();
				ploss.affineTransform(1, 1);
				ploss.affineTransform(lanes, 0);
				ploss.affineTransform(length, 0);
				ploss.affineTransform(tp, 0);
				ploss.constraintLB(0);
			}
			plossSum.add(ploss);
		}
		return res;
	}
	
	/**
	 * Updates configuration summary.
	 * @param cs configuration summary.
	 */
	public void updateConfigurationSummary(AbstractConfigurationSummary cs) {
		if (cs == null)
			return;
		super.updateConfigurationSummary(cs);
		((ConfigurationSummaryHWC)cs).updateFastestLink(this);
		((ConfigurationSummaryHWC)cs).updateSlowestLink(this);
		((ConfigurationSummaryHWC)cs).updateMinTTLink(this);
		return;
	}
	
	/**
	 * Additional initialization.
	 * @return <code>true</code> if operation succeeded, <code>false</code> - otherwise.
	 * @throws ExceptionConfiguration, ExceptionDatabase
	 */
	public boolean initialize() throws ExceptionConfiguration, ExceptionDatabase {
		boolean res = super.initialize();
		if (getBeginNode() == null) {
			qSize.copy(density0);
			qSize.affineTransform(length, 0);
		}
		else
			qSize = new AuroraIntervalVector(((SimulationSettingsHWC)myNetwork.getContainer().getMySettings()).countVehicleTypes());
		density.copy(density0);
		speed.setCenter(0.0, 0.0);
		resetSums();
		if (saveState == 1)
			saveState = 0;
		if (saveState == 2)
			saveState = 3;
		tsV = 0;
		return res;
	}
	
	/**
	 * Validates Link configuration.<br>
	 * Checks if dynamics is assigned.
	 * @return <code>true</code> if configuration is correct, <code>false</code> - otherwise.
	 * @throws ExceptionConfiguration
	 */
	public boolean validate() throws ExceptionConfiguration {
		boolean res = super.validate();
		if (myDynamics == null) {
			myNetwork.addConfigurationError(new ErrorConfiguration(this, "No dynamics assigned."));
			res = false;
			//throw new ExceptionConfiguration(this, "No dynamics assigned.");
		}
		return res;
	}
	
	/**
	 * Returns dynamics.
	 */
	public DynamicsHWC getDynamics() {
		return myDynamics;
	}
	
	/**
	 * Checks if upper input bound should be subtracted from the upstream lower density bound.
	 */
	public boolean isInputUpperBoundFirst() {
		return inUpperBoundFirst;
	}
	
	/**
	 * Checks if upper output bound should be added to the downstream lower density bound.
	 */
	public boolean isOutputUpperBoundFirst() {
		return outUpperBoundFirst;
	}
	
	
	/**
	 * Returns flow generated by the link.
	 */
	public AuroraIntervalVector getFlow() {
		return (AuroraIntervalVector)myDynamics.computeFlow(this);
	}
	
	/**
	 * Returns best case flow bounds generated by the link.
	 */
	public AuroraIntervalVector getFlowL() {
		return (AuroraIntervalVector)myDynamics.computeFlowL(this);
	}
	
	/**
	 * Returns worst case flow bounds generated by the link.
	 */
	public AuroraIntervalVector getFlowU() {
		return (AuroraIntervalVector)myDynamics.computeFlowU(this);
	}
	
	/**
	 * Returns flow that actually leaves the link.
	 */
	public AuroraIntervalVector getActualFlow() {
		AuroraIntervalVector flw = new AuroraIntervalVector();
		AbstractNode en = getEndNode();
		if (en != null)
			flw.copy((AuroraIntervalVector)en.getInputs().get(en.getPredecessors().indexOf(this)));
		else {
			AuroraIntervalVector flwL, flwU;
			if (myNetwork.getContainer().getMySettings().isPrediction()) {
				flwL = getFlowL();
				flwU = getFlowU();
			}
			else {
				flwL = getFlow();
				flwU = new AuroraIntervalVector();
				flwU.copy(flwL);
				flwL.toLower();
				flwU.toUpper();
			}
			flwL.toUpper();
			flwU.toLower();
			AuroraInterval fsumL = flwL.sum();
			AuroraInterval fsumU = flwU.sum();
			double lbc = Math.min(1, getCapacityValue().getUpperBound() / fsumL.getCenter());
			double ubc = Math.min(1, getCapacityValue().getLowerBound() / fsumU.getCenter());
			if (lbc*fsumL.getCenter() > ubc*fsumU.getCenter()) {
				outUpperBoundFirst = true;
				for (int i = 0; i < flwU.size(); i++)
					flwU.get(i).setCenter(ubc*flwU.get(i).getCenter());
				int count = 0;
				while ((lbc < 1) && (count < flwL.size())) {
					double ns = 0;
					count = 0;
					for (int i = 0; i < flwL.size(); i++) {
						double c = 1;
						double v = flwL.get(i).getCenter();
						if (v > Util.EPSILON)
							c = flwU.get(i).getCenter()/v;
						if (lbc <= c) {
							v = c * v;
							flwL.get(i).setCenter(v);
							ns += v;
						}
						else {
							flwL.get(i).setCenter(lbc * v);
							count++;
						}
					}
					if (flwL.sum().getCenter() - ns < Util.EPSILON)
						lbc = 1;
					else
						lbc = (getCapacityValue().getUpperBound() - ns)/(flwL.sum().getCenter() - ns);
				}
			}
			else {
				outUpperBoundFirst = false;
				for (int i = 0; i < flwL.size(); i++)
					flwL.get(i).setCenter(lbc*flwL.get(i).getCenter());
				int count = 0;
				while ((ubc < 1) && (count < flwU.size())) {
					double ns = 0;
					count = 0;
					for (int i = 0; i < flwU.size(); i++) {
						double c = 1;
						double v = flwU.get(i).getCenter();
						if (v > Util.EPSILON)
							c = flwL.get(i).getCenter()/v;
						if (ubc <= c) {
							v = c * v;
							flwU.get(i).setCenter(v);
							ns += v;
						}
						else {
							flwU.get(i).setCenter(ubc * v);
							count++;
						}
					}
					if (flwU.sum().getCenter() - ns < Util.EPSILON)
						ubc = 1;
					else
						ubc = (getCapacityValue().getLowerBound() - ns)/(flwU.sum().getCenter() - ns);
				}
			}
			flw.copy(flwL);
			for (int i = 0; i < flw.size(); i++) {
				flw.get(i).setBounds(flwL.get(i).getLowerBound(), flwU.get(i).getUpperBound());
				flw.get(i).toLower(Util.EPSILON);
			}
		}
		return flw;
	}
	
	/**
	 * Returns average input flow.
	 */
	public AuroraIntervalVector getAverageInFlow() {
		AuroraIntervalVector aif = new AuroraIntervalVector();
		aif.copy(inflowSum);
		if (tsCount > 0)
			aif.affineTransform(1.0/tsCount, 0);
		return aif;
	}
	
	/**
	 * Returns average output flow.
	 */
	public AuroraIntervalVector getAverageOutFlow() {
		AuroraIntervalVector aof = new AuroraIntervalVector();
		aof.copy(outflowSum);
		if (tsCount > 0)
			aof.affineTransform(1.0/tsCount, 0);
		return aof;
	}
	
	/**
	 * Returns maximum flow that can be accepted from upstream node.
	 */
	public AuroraInterval getCapacity() {
		return (AuroraInterval)myDynamics.computeCapacity(this);
	}
	
	/**
	 * Returns lower bounds maximum flow that can be accepted from upstream node.
	 */
	public AuroraInterval getCapacityL() {
		return (AuroraInterval)myDynamics.computeCapacityL(this);
	}
	
	/**
	 * Returns upper bounds of maximum flow that can be accepted from upstream node.
	 */
	public AuroraInterval getCapacityU() {
		return (AuroraInterval)myDynamics.computeCapacityU(this);
	}
	
	/**
	 * Returns mean speed of traffic in the link.
	 */
	public AuroraInterval getSpeed() {
		AuroraInterval s = new AuroraInterval();
		s.copy(speed);
		return s;
	}
	
	/**
	 * Returns average mean speed of traffic in the link.
	 */
	public AuroraInterval getAverageSpeed() {
		AuroraInterval as = new AuroraInterval();
		as.copy(speedSum);
		if (tsCount > 0)
			as.affineTransform(1.0/tsCount, 0);
		return as;
	}
	
	/**
	 * Returns number of lanes in the link.
	 */
	public final double getLanes() {
		return lanes;
	}
	
	/**
	 * Returns the width of the link.
	 */
	public final double getWidth() {
		return getLanes();
	}
	
	/**
	 * Returns capacity drop.
	 */
	public final double getCapacityDrop() {
		return capacityDrop;
	}
	
	/**
	 * Checks if the upper bound of the weaving factor should apply to lower density bound.
	 */
	public final boolean isWFUpperBoundFirst() {
		return wfUpperBoundFirst;
	}
	
	/**
	 * Checks if the upper bound of the input weaving factor should apply to lower density bound.
	 */
	public final boolean isIWFUpperBoundFirst() {
		return iwfUpperBoundFirst;
	}
	
	/**
	 * Returns terminal node.
	 */
	public final AbstractNodeHWC getTerminalNode() {
		return terminalNode;
	}
	
	/**
	 * Returns the weaving factor.
	 */
	public final AuroraInterval getWeavingFactor() {
		return currentWeavingFactor;
	}
	
	/**
	 * Returns the input weaving factor.
	 */
	public final AuroraInterval getInputWeavingFactor() {
		return new AuroraInterval(inputWeavingFactor.getCenter(), inputWeavingFactor.getSize());
	}
	
	/**
	 * Returns the output weaving factors.
	 */
	public final double[] getOutputWeavingFactors() {
		if ((outputWeavingFactors != null) && (outputWeavingFactors.length > 0)) {
			int n = outputWeavingFactors.length;
			double[] owf = new double[n];
			for (int i = 0; i < n; i++)
				owf[i] = outputWeavingFactors[i];
			return owf;
		}
		return null;
	}
	
	/**
	 * Returns capacity range.
	 */
	public final AuroraInterval getMaxFlowRange() {
		if (myNetwork.getContainer().isSimulation() && !myNetwork.getContainer().getMySettings().isPrediction())
			return new AuroraInterval(flowMax, 0);
		return new AuroraInterval(flowMax, flowMaxRange.getSize());
	}
	
	/**
	 * Returns maximum flow that can be achieved in the link.
	 */
	public final double getMaxFlow() {
		return flowMax;
	}
	
	/**
	 * Returns critical density range for the link.
	 */
	public final AuroraInterval getCriticalDensityRange() {
		AuroraInterval cdr = new AuroraInterval();
		cdr.setBounds(flowMaxRange.getLowerBound()/getV(), flowMaxRange.getUpperBound()/getV());
		return cdr;
	}
	
	/**
	 * Returns critical density for the link.
	 */
	public final double getCriticalDensity() {
		return densityCritical;
	}
	
	/**
	 * Returns jam density range for the link.
	 */
	public final AuroraInterval getJamDensityRange() {
		AuroraInterval jdr = new AuroraInterval();
		double lb = getMaxFlowRange().getLowerBound()/getW() + getCriticalDensityRange().getLowerBound();
		double ub = getMaxFlowRange().getUpperBound()/getW() + getCriticalDensityRange().getUpperBound();
		jdr.setBounds(lb, ub);
		return jdr;
	}
	
	/**
	 * Returns jam density for the link.
	 */
	public final double getJamDensity() {
		return densityJam;
	}
	
	/**
	 * Returns queue size.
	 */
	public final AuroraIntervalVector getQueue() {
		AuroraIntervalVector q = new AuroraIntervalVector();
		q.copy(qSize);
		return q;
	}
	
	/**
	 * Returns queue limit.
	 */
	public final double getQueueMax() {
		return qMax;
	}
	
	/**
	 * Returns free flow speed.
	 */
	public final double getV() { // free-flow speed
		return (flowMax / densityCritical);
	}
	
	/**
	 * Returns congestion wave speed.
	 */
	public final double getW() { // congestion wave speed
		return (flowMax / (densityJam - densityCritical));
	}
	
	/**
	 * Returns initial density.
	 */
	public final AuroraIntervalVector getInitialDensity() {
		AuroraIntervalVector den = new AuroraIntervalVector();
		den.copy(density0);
		return den;
	}
	
	/**
	 * Returns density.
	 */
	public final AuroraIntervalVector getDensity() {
		AuroraIntervalVector den = new AuroraIntervalVector();
		den.copy(density);
		return den;
	}

	/**
	 * Returns average density.
	 */
	public final AuroraIntervalVector getAverageDensity() {
		AuroraIntervalVector ad = new AuroraIntervalVector();
		ad.copy(densitySum);
		if (tsCount > 0)
			ad.affineTransform(1.0/tsCount, 0);
		return ad;
	}
	
	
	/**
	 * Returns VHT.
	 */
	public final AuroraInterval getVHT() {
		AuroraInterval v = new AuroraInterval();
		v.copy(vht);
		return v;
	}
	
	/**
	 * Returns sum of VHT.
	 */
	public final AuroraInterval getSumVHT() {
		AuroraInterval v = new AuroraInterval();
		v.copy(vhtSum);
		return v;
	}
	
	/**
	 * Returns VMT.
	 */
	public final AuroraInterval getVMT() {
		AuroraInterval v = new AuroraInterval();
		v.copy(vmt);
		return v;
	}
	
	/**
	 * Returns sum of VMT.
	 */
	public final AuroraInterval getSumVMT() {
		AuroraInterval v = new AuroraInterval();
		v.copy(vmtSum);
		return v;
	}
	
	/**
	 * Returns delay.
	 */
	public final AuroraInterval getDelay() {
		AuroraInterval v = new AuroraInterval();
		v.copy(delay);
		return v;
	}
	
	/**
	 * Returns sum of delay.
	 */
	public final AuroraInterval getSumDelay() {
		AuroraInterval v = new AuroraInterval();
		v.copy(delaySum);
		return v;
	}
	
	/**
	 * Returns productivity loss.
	 */
	public final AuroraInterval getPLoss() {
		AuroraInterval v = new AuroraInterval();
		v.copy(ploss);
		return v;
	}
	
	/**
	 * Returns sum of productivity loss.
	 */
	public final AuroraInterval getSumPLoss() {
		AuroraInterval v = new AuroraInterval();
		v.copy(plossSum);
		return v;
	}
	
	/**
	 * Returns occupancy.
	 */
	public final AuroraInterval getOccupancy() {
		double olb = density.sum().getLowerBound() * currentWeavingFactor.getLowerBound() / getJamDensityRange().getUpperBound();
		double oub = density.sum().getUpperBound() * currentWeavingFactor.getUpperBound() / getJamDensityRange().getLowerBound();
		olb = Math.min(olb, 1);
		oub = Math.min(oub, 1);
		return new AuroraInterval((olb+oub)/2, Math.abs(oub-olb));
	}
	
	/**
	 * Returns demand.
	 */
	public final AuroraIntervalVector getDemand() {
		AuroraIntervalVector dmnd = new AuroraIntervalVector();
		if (extDemandVal != null) {
			dmnd.copy(extDemandVal);
			return dmnd;
		}
		double t = myNetwork.getSimTime(); // simulation time (in hours)
		t -= demandST;  // adjust for demand prof
		int idx = (int)Math.floor(t/demandTP);
		int n = procDemand.size() - 1; // max index of the demand profile
		if ((idx < 0) || (n < 0)) //empty
			return new AuroraIntervalVector(((SimulationSettingsHWC)myNetwork.getContainer().getMySettings()).countVehicleTypes());
		if ((idx < 0) || (idx > n))
			idx = n;
		dmnd.copy(procDemand.get(idx));
		dmnd.affineTransform(demandKnobs, 0);		
		return dmnd;
	}
	
	/**
	 * Returns demand value taking into account possible external demand.
	 */
	public final AuroraIntervalVector getDemandValue() {
		AuroraIntervalVector ord = new AuroraIntervalVector();
		if (extDemandVal != null) {
			ord.copy(extDemandVal);
			return ord;
		}
		AuroraIntervalVector orq = new AuroraIntervalVector();
		ord.copy((AuroraIntervalVector)getDemand());
		orq.copy((AuroraIntervalVector)getQueue());
		orq.affineTransform(1/getMyNetwork().getTP(), 0);
		ord.add(orq);
		double lbc = getMaxFlowRange().getLowerBound() / ord.sum().getLowerBound();
		double ubc = getMaxFlowRange().getUpperBound() / ord.sum().getUpperBound();
		if (lbc < 1)
			for (int i = 0; i < ord.size(); i++)
				ord.get(i).affineTransformLB(lbc, 0);
		int count = 0;
		while ((ubc < 1) && (count < ord.size())) {
			double ns = 0;
			count = 0;
			for (int i = 0; i < ord.size(); i++) {
				double c = 1;
				double v = ord.get(i).getUpperBound();
				if (v > Util.EPSILON)
					c = ord.get(i).getLowerBound()/v;
				if (ubc <= c) {
					v = c * v;
					ord.get(i).setUpperBound(v);
					ns += v;
				}
				else {
					ord.get(i).setUpperBound(ubc * v);
					count++;
				}
				ord.get(i).toLower(Util.EPSILON);
			}
			if (ord.sum().getCenter() - ns < Util.EPSILON)
				ubc = 1;
			else
				ubc = (getMaxFlowRange().getUpperBound() - ns)/(ord.sum().getCenter() - ns);
		}
		return ord;
	}
	
	/**
	 * Returns demand value taking into account possible external demand.
	 * Do not impose capacity restriction.
	 */
	public final AuroraIntervalVector getDemandValue2() {
		AuroraIntervalVector ord = new AuroraIntervalVector();
		if (extDemandVal != null) {
			ord.copy(extDemandVal);
			return ord;
		}
		AuroraIntervalVector orq = new AuroraIntervalVector();
		ord.copy((AuroraIntervalVector)getDemand());
		orq.copy((AuroraIntervalVector)getQueue());
		orq.affineTransform(1/getMyNetwork().getTP(), 0);
		ord.add(orq);
		return ord;
	}
	
	/**
	 * Returns demand vector.
	 */
	public final Vector<AuroraIntervalVector> getDemandVector() {
		return demand;
	}
	
	/**
	 * Returns demand vector as text buffer. 
	 * @return buf text buffer.
	 */
	public final String getDemandVectorAsString() {
		String buf = new String();
		int n = demand.size();
		if (n < 1)
			buf += "0.0";
		else {
			buf += demand.get(0).toStringWithInverseWeights(((SimulationSettingsHWC)myNetwork.getContainer().getMySettings()).getVehicleWeights(), false);
			for (int i = 1; i < n; i++)
				buf += ", " + demand.get(i).toStringWithInverseWeights(((SimulationSettingsHWC)myNetwork.getContainer().getMySettings()).getVehicleWeights(), false);
		}
		return buf;
	}
	
	/**
	 * Returns demand knob values.
	 */
	public final double[] getDemandKnobs() {
		double[] v = new double[demandKnobs.length];
		for (int i = 0; i < demandKnobs.length; i++)
			v[i] = demandKnobs[i]; 
		return v;
	}
	
	/**
	 * Returns demand knob values as string.
	 */
	public final String getDemandKnobsAsString() {
		boolean allequal = true;
		String buf = "";
		double val = demandKnobs[0];
		for (int i = 0; i < demandKnobs.length; i++) {
			if (i > 0) {
				buf += ":";
				if (demandKnobs[i] != val)
					allequal = false;
			}
			buf += Double.toString(demandKnobs[i]);
		}
		if (allequal)
			buf = Double.toString(val);
		return buf;
	}
	
	/**
	 * Returns demand uncertainty values as string
	 */
	public final String getDemandUncertaintyAsString() {
		NumberFormat form = NumberFormat.getInstance();
		form.setMinimumFractionDigits(0);
		form.setMaximumFractionDigits(4);
		String buf = "";
		double[] du = new double[((SimulationSettingsHWC)myNetwork.getContainer().getMySettings()).countVehicleTypes()];
		for (int i = 0; i < demand.size(); i++) {
			AuroraIntervalVector dvec = demand.get(i);
			for (int j = 0; j < du.length; j++) {
				double ui = 100*dvec.get(j).getSize()/(2*dvec.get(j).getCenter());
				if (i == 0)
					du[j] = ui;
				else
					du[j] = Math.max(du[j], ui);
			}
		}
		boolean eq = true;
		for (int i = 0; i < du.length; i++) {
			if (i > 0)
				buf += ":";
			if (Math.abs(du[0]-du[i]) > 0.0001)
				eq = false;
			buf += form.format(du[i]);
		}
		if (eq)
			buf = form.format(du[0]);
		return buf;
	}
	
	/**
	 * Returns the frequency of demand value change.
	 */
	public final double getDemandTP() {
		return demandTP;
	}
	
	/**
	 * Returns the start time of the demand profile in hours.
	 */
	public final double getDemandStartTime() {
		return demandST;
	}
	
	/**
	 * Returns the demand value set by external entity.
	 */
	public final AuroraIntervalVector getExternalDemandValue() {
		if (extDemandVal == null)
			return null;
		AuroraIntervalVector v = new AuroraIntervalVector();
		v.copy(extDemandVal);
		return v;
	}
	
	/**
	 * Returns downstream capacity.
	 */
	public final AuroraInterval getCapacityValue() {
		AuroraInterval cv = new AuroraInterval();
		if (extCapVal != null)
			return getMaxFlowRange();
		double t = myNetwork.getSimTime(); // simulation time (in hours)
		t -= capacityST; // adjust by capacity profile start time
		int idx = (int)Math.floor(t/capacityTP);
		int n = capacity.size() - 1; // max index of the capacity profile
		if (idx > n)
			idx = n;
		if ((idx < 0) || (getEndNode() != null))
			return getMaxFlowRange();
		cv.copy(capacity.get(idx));
		return cv;
	}
	
	/**
	 * Returns capacity vector.
	 */
	public final Vector<AuroraInterval> getCapacityVector() {
		return capacity;
	}
	
	/**
	 * Returns capacity vector as text buffer. 
	 * @return buf text buffer.
	 */
	public final String getCapacityVectorAsString() {
		String buf = new String();
		int n = capacity.size();
		if (n > 0) {
			buf += capacity.get(0).toString();
			for (int i = 1; i < n; i++)
				buf += ", " + capacity.get(i).toString();
		}
		return buf;
	}
	
	/**
	 * Returns the capacity value set by external entity.
	 */
	public final double getCapacityTP() {
		return capacityTP;
	}
	
	/**
	 * Returns the start time of the capacity profile in hours.
	 */
	public final double getCapacityStartTime() {
		return capacityST;
	}
	
	/**
	 * Returns the frequency of capacity value change.
	 */
	public final AuroraInterval getExternalCapacityValue() {
		if (extCapVal == null)
			return null;
		AuroraInterval v = new AuroraInterval();
		v.copy(extCapVal);
		return v;
	}
	
	/**
	 * Returns instantaneous travel time.
	 */
	public AuroraInterval getTravelTime() {
		AuroraInterval tt = new AuroraInterval();
		AuroraInterval v = new AuroraInterval();
		v.copy(speed);
		v.constraintLB(Util.EPSILON);
		if (getBeginNode() != null) {
			double lb = length/v.getUpperBound();
			double ub = length/v.getLowerBound();
			if ((lb == Double.NaN) || (lb == Double.POSITIVE_INFINITY))
				lb = Double.MAX_VALUE;
			if ((ub == Double.NaN) || (ub == Double.POSITIVE_INFINITY))
				ub = Double.MAX_VALUE;
			tt.setBounds(lb, ub);
		}
		else {
			tt.copy(qSize.sum());
			AuroraInterval af = getActualFlow().sum();
			af.constraintLB(Util.EPSILON);
			tt.quotient(af);
		}
		tt.constraintUB(24);
		tt.constraintLB(getMinTravelTime());
		return tt; 
	}
	
	/**
	 * Returns minimal travel time.
	 */
	public final double getMinTravelTime() {
		double tt = 0.0;
		if (getBeginNode() != null)
			if (getV() > 0.0)
				tt = length/getV();
			else
				tt = Double.MAX_VALUE;
		return tt;
	}
	
	/**
	 * Assigns Network of which current Link is part.
	 * @param x Complex Node.
	 * @return <code>true</code> if successful, <code>false</code> - otherwise.
	 */
	public synchronized boolean setMyNetwork(AbstractNodeComplex x) {
		boolean res = super.setMyNetwork(x);
		if (!res)
			return res;
		int sz = ((SimulationSettingsHWC)myNetwork.getContainer().getMySettings()).countVehicleTypes();
		demandKnobs = new double[sz];
		for (int i = 0; i < sz; i++)
			demandKnobs[i] = 1;
		return true;
	}
	
	/**
	 * Assigns queue limit.
	 * @param x queue limit.
	 * @return <code>true</code> if operation succeeded, <code>false</code> - otherwise.
	 */
	public synchronized boolean setQueueMax(double x) {
		if (x >= 0.0)
			qMax = x;
		else
			return false;
		return true;
	}
	
	/**
	 * Assigns number of lanes.<br>
	 * Adjusts the fundamental diagram accordingly.
	 * @param x number of lanes.
	 * @return <code>true</code> if operation succeeded, <code>false</code> - otherwise.
	 */
	public synchronized boolean setLanes(double x) {
		if (x < 1.0)
			x = 1.0;
		if (lanes > 0) {
			flowMax = flowMax * (x/lanes);
			densityCritical = densityCritical * (x/lanes);
			densityJam = densityJam * (x/lanes);
		}
		lanes = x;
		return true;
	}
	
	/**
	 * Assigns capacity drop.
	 * @param x capacity drop.
	 * @return <code>true</code> if operation succeeded, <code>false</code> - otherwise.
	 */
	public synchronized boolean setCapacityDrop(double x) {
		if (x < 0.0)
			return false;
		capacityDrop = x;
		return true;
	}
	
	/**
	 * Sets the flag indicating that the upper bound of the input flow
	 * should be added to the lower bound of the density and vice versa, or not.
	 * @param x bound order flag.
	 * @return <code>true</code> if operation succeeded, <code>false</code> - otherwise.
	 */
	public synchronized boolean inverseInputBounds(boolean x) {
		inUpperBoundFirst = x;
		return true;
	}
	
	/**
	 * Sets the flag indicating that the upper bound of the output flow
	 * should be subtracted from the lower bound of the density and vice versa, or not.
	 * @param x bound order flag.
	 * @return <code>true</code> if operation succeeded, <code>false</code> - otherwise.
	 */
	public synchronized boolean inverseOutputBounds(boolean x) {
		outUpperBoundFirst = x;
		return true;
	}
	
	/**
	 * Sets the flag indicating whether the upper bound of the weaving factor
	 * should apply to the lower bound of the density and vice versa, or not.
	 * @param x bound order flag.
	 * @return <code>true</code> if operation succeeded, <code>false</code> - otherwise.
	 */
	public synchronized boolean inverseWFBounds(boolean x) {
		wfUpperBoundFirst = x;
		return true;
	}
	
	/**
	 * Sets the flag indicating whether the upper bound of the input weaving factor
	 * should apply to the lower bound of the incoming flow and vice versa, or not.
	 * @param x bound order flag.
	 * @return <code>true</code> if operation succeeded, <code>false</code> - otherwise.
	 */
	public synchronized boolean inverseIWFBounds(boolean x) {
		iwfUpperBoundFirst = x;
		return true;
	}
	
	/**
	 * Assigns weaving factor.
	 * @param x weaving factor.
	 * @return <code>true</code> if operation succeeded, <code>false</code> - otherwise.
	 */
	public synchronized boolean setWeavingFactor(AuroraInterval x) {
		if (x == null)
			return false;
		currentWeavingFactor.copy(x);
		currentWeavingFactor.constraintLB(1);
		return true;
	}
	
	/**
	 * Assigns input flow weaving factor.
	 * @param x input flow weaving factor.
	 * @return <code>true</code> if operation succeeded, <code>false</code> - otherwise.
	 */
	public synchronized boolean setInputWeavingFactor(AuroraInterval x) {
		if (x == null)
			return false;
		inputWeavingFactor.copy(x);
		inputWeavingFactor.constraintLB(1);
		return true;
	}
	
	/**
	 * Assigns output flow weaving factors.
	 * @param x output flow weaving factors.
	 * @return <code>true</code> if operation succeeded, <code>false</code> - otherwise.
	 */
	public synchronized boolean setOutputWeavingFactors(double[] x) {
		if ((x == null) || (x.length != density.size()))
			return false;
		if (outputWeavingFactors == null)
			outputWeavingFactors = new double[x.length];
		for (int i = 0; i < outputWeavingFactors.length; i++)
			outputWeavingFactors[i] = x[i];
		return true;
	}
	
	/**
	 * Assigns range for maximum flow.
	 * @param x interval size maximum flow.
	 * @return <code>true</code> if operation succeeded, <code>false</code> - otherwise.
	 */
	public synchronized boolean setMaxFlowRange(double x) {
		if ((x < 0.0) || (x > 1))
			return false;
		flowMaxRange.setCenter(flowMax, 2*x*flowMax);
		return true;
	}
	
	/**
	 * Assigns maximum flow.
	 * @param x maximum flow.
	 * @return <code>true</code> if operation succeeded, <code>false</code> - otherwise.
	 */
	public synchronized boolean setMaxFlow(double x) {
		if (x < 0.0)
			return false;
		double rf = Math.min(1, flowMaxRange.getSize()/(2*flowMax));
		flowMax = x;
		flowMaxRange.setCenter(flowMax, 2*rf*flowMax);
		return true;
	}
	
	/**
	 * Assigns critical density.
	 * @param x critical density.
	 * @return <code>true</code> if operation succeeded, <code>false</code> - otherwise.
	 */
	public synchronized boolean setCriticalDensity(double x) {
		if ((x >= 0.0) && (x <= densityJam))
			densityCritical = x;
		else
			return false;
		return true;
	}
	
	/**
	 * Assigns jam density.
	 * @param x jam density.
	 * @return <code>true</code> if operation succeeded, <code>false</code> - otherwise.
	 */
	public synchronized boolean setJamDensity(double x) {
		if (x >= densityCritical)
			densityJam = x;
		else
			return false;
		return true;
	}
	
	/**
	 * Assigns dynamics.
	 * @param dyn dynamics object.
	 * @return <code>true</code> if operation succeeded, <code>false</code> - otherwise.
	 */
	public synchronized boolean setDynamics(DynamicsHWC dyn) {
		if (dyn == null)
			return false;
		myDynamics = dyn;
		return true;
	}
	
	/**
	 * Sets random maximum flow value and adjusts fundamental diagram accordingly.
	 * @return <code>true</code> if operation succeeded, <code>false</code> - otherwise.
	 */
	public synchronized boolean randomizeFD() {
		flowMaxRange.constraintLB(0);
		flowMaxRange.randomize();
		double fm = flowMaxRange.getCenter();
		double cd = fm/getV();
		double jd = cd + (fm/getW());
		return setFD(fm, cd, jd);
	}
	
	/**
	 * Assigns parameters of the fundamental diagram.
	 * @param fmax maximum flow.
	 * @param rhoc critical density.
	 * @param rhoj jam density.
	 * @return <code>true</code> if operation succeeded, <code>false</code> - otherwise.
	 */
	public synchronized boolean setFD(double fmax, double rhoc, double rhoj) {
		boolean res = false;
		if ((fmax >= 0.0) && (rhoc >= 0.0) && (rhoj >= 0.0) && (rhoc <= rhoj)) {
			flowMax = fmax;
			densityCritical = rhoc;
			densityJam = rhoj;
			res = true;
		}
		return res;
	}
	
	/**
	 * Assigns parameters of the fundamental diagram.
	 * @param fmax maximum flow.
	 * @param rhoc critical density.
	 * @param rhoj jam density.
	 * @param capd capacity drop;
	 * @return <code>true</code> if operation succeeded, <code>false</code> - otherwise.
	 */
	public synchronized boolean setFD(double fmax, double rhoc, double rhoj, double capd) {
		boolean res = false;
		if ((fmax >= 0.0) && (rhoc >= 0.0) && (rhoj >= 0.0) && (rhoc <= rhoj)) {
			double rf = Math.min(1, flowMaxRange.getSize()/(2*flowMax));
			flowMax = fmax;
			densityCritical = rhoc;
			densityJam = rhoj;
			capacityDrop = Math.max(0, Math.min(fmax, capd));
			flowMaxRange.setCenter(flowMax, 2*rf*flowMax);
			res = true;
		}
		return res;
	}
	
	/**
	 * Initializes the fundamental diagram with defaults.
	 * @return <code>true</code> if operation succeeded, <code>false</code> - otherwise.
	 */
	public synchronized boolean defaultFD() {
		flowMax = 1800 * lanes;
		flowMaxRange.setCenter(flowMax, 0);
		densityCritical = 30.0 * lanes;
		densityJam = 150 * lanes;
		capacityDrop = 0.0;
		return true;
	}
	
	/**
	 * Modifies fundamental diagram by assigning free flow speed.<br>
	 * Maximum flow and congestion wave speed remain untouched.
	 * @param x free flow speed.
	 * @return <code>true</code> if operation succeeded, <code>false</code> - otherwise.
	 */
	public synchronized boolean setV(double x) { // max flow & jam density are fixed
		double w = flowMax / (densityJam - densityCritical);
		if (x > 0.0) {
			densityCritical = flowMax / x;
			densityJam = densityCritical + (flowMax / w);
		}
		else
			return false;
		return true;
	}
	
	/**
	 * Modifies fundamental diagram by assigning congestion wave speed.<br>
	 * Maximum flow and critical density remain untouched.
	 * @param x congestion wave speed.
	 * @return <code>true</code> if operation succeeded, <code>false</code> - otherwise.
	 */
	public synchronized boolean setW(double x) { // max flow & critical density are fixed
		if (x > 0.0)
			densityJam = densityCritical + (flowMax / x);
		else
			return false;
		return true;
	}
	
	/**
	 * Sets initial density value.<br>
	 * Used to set initial condition.
	 * @param x density value.
	 * @return <code>true</code> if operation succeeded, <code>false</code> - otherwise.
	 */
	public synchronized boolean setInitialDensity(AuroraIntervalVector x) {
		if (x == null)
			return false;
		boolean res = density0.copy(x);
		density0.constraintLB(0);
		double s = densityJam / density.sum().getUpperBound();
		if (s < 1)
			for (int i = 0; i < density0.size(); i++)
				density0.get(i).setUpperBound(s * density0.get(i).getUpperBound());
		density.copy(density0);
		return res;
	}
	
	/**
	 * Sets initial density value from text buffer.<br>
	 * Used to set initial condition.
	 * @param buf density value as text buffer.
	 * @return <code>true</code> if operation succeeded, <code>false</code> - otherwise.
	 */
	public synchronized boolean setInitialDensity(String buf) {
		if ((buf == null) || buf.isEmpty())
			return false;
		density0 = new AuroraIntervalVector(((SimulationSettingsHWC)myNetwork.getContainer().getMySettings()).countVehicleTypes());
		if (myNetwork.getContainer().isSimulation())
			density0.setIntervalVectorFromString(buf);
		else
			density0.setRawIntervalVectorFromString(buf);
		density0.affineTransform(((SimulationSettingsHWC)myNetwork.getContainer().getMySettings()).getVehicleWeights(), 0);
		density0.constraintLB(0);
		double s = densityJam / density.sum().getUpperBound();
		if (s < 1)
			for (int i = 0; i < density0.size(); i++)
				density0.get(i).setUpperBound(s * density0.get(i).getUpperBound());
		density.copy(density0);
		return true;
	}
	
	/**
	 * Sets demand vector.<br>
	 * @param x demand vector.
	 * @return <code>true</code> if operation succeeded, <code>false</code> - otherwise.
	 */
	public synchronized boolean setDemandVector(Vector<AuroraIntervalVector> x) {
		if (x == null)
			return false;
		demand = x;
		return true;
	}
	
	/**
	 * Sets demand vector from string.
	 * @param buf text buffer.
	 * @return <code>true</code> if operation succeeded, <code>false</code> - otherwise.
	 */
	public synchronized boolean setDemandVector(String buf) {
		if (buf == null)
			return false;
		StringTokenizer st = new StringTokenizer(buf, ", \t");
		demand.clear();
		while (st.hasMoreTokens()) {
			AuroraIntervalVector v = new AuroraIntervalVector(((SimulationSettingsHWC)myNetwork.getContainer().getMySettings()).countVehicleTypes());
			if (myNetwork.getContainer().isSimulation())
				v.setIntervalVectorFromString(st.nextToken());
			else
				v.setRawIntervalVectorFromString(st.nextToken());
			v.affineTransform(((SimulationSettingsHWC)myNetwork.getContainer().getMySettings()).getVehicleWeights(), 0);
			v.constraintLB(0);
			/*// FIXME: Redistribute demand
			double[] rv = {0.78, 0.22};
			v.redistribute(rv);//*/
			demand.add(v);
			AuroraIntervalVector v2 = new AuroraIntervalVector();
			v2.copy(v);
			if (myNetwork.getContainer().isSimulation() && (!myNetwork.getContainer().getMySettings().isPrediction()))
				v2.randomize();
			procDemand.add(v2);
		}
		return true;
	}
	
	/**
	 * Sets demand knob values.
	 * @param x demand knob values.
	 * @return <code>true</code> if operation succeeded, <code>false</code> - otherwise.
	 */
	public synchronized boolean setDemandKnobs(double[] x) {
		if ((x == null) || (x.length < 1))
			return false;
		int sz = Math.min(x.length, demandKnobs.length);
		for (int i = 0; i < sz; i++)
			demandKnobs[i] = Math.max(0, x[i]);
		if (sz == 1)
			for (int i = sz; i < demandKnobs.length; i++)
				demandKnobs[i] = demandKnobs[0];
		return true;
	}
	
	/**
	 * Sets demand knob values from given string buffer.
	 * @param x string with column separated demand knob values.
	 * @return <code>true</code> if operation succeeded, <code>false</code> - otherwise.
	 */
	public synchronized boolean setDemandKnobs(String x) {
		if ((x == null) || (x.isEmpty()))
			return false;
		StringTokenizer st = new StringTokenizer(x, ": ");
		if (st.countTokens() < 1)
			return false;
		int sz = Math.min(demandKnobs.length, st.countTokens());
		for (int i = 0; i < sz; i++) {
			try {
				demandKnobs[i] = Math.max(0, Double.parseDouble(st.nextToken()));
			}
			catch(Exception e) {
				demandKnobs[i] = 1;
			}
		}
		for (int i = sz; i < demandKnobs.length; i++) {
			if (sz == 1)
				demandKnobs[i] = demandKnobs[0];
			else
				demandKnobs[i] = 1;
		}
		return true;
	}
	
	/**
	 * Sets demand uncertainty from given string buffer.
	 * @param x string with column separated demand knob values.
	 * @return <code>true</code> if operation succeeded, <code>false</code> - otherwise.
	 */
	public synchronized boolean setDemandUncertainty(String x) {
		if ((x == null) || (x.isEmpty()))
			return false;
		StringTokenizer st = new StringTokenizer(x, ": ");
		if (st.countTokens() < 1)
			return false;
		int sz = ((SimulationSettingsHWC)myNetwork.getContainer().getMySettings()).countVehicleTypes();
		double[] du = new double[sz];
		sz = Math.min(du.length, st.countTokens());
		for (int i = 0; i < sz; i++) {
			try {
				du[i] = Math.min(1, Math.max(0, Double.parseDouble(st.nextToken())/100));
			}
			catch(Exception e) {
				du[i] = 0;
			}
		}
		for (int i = sz; i < du.length; i++) {
			if (sz == 1)
				du[i] = du[0];
			else
				du[i] = 0;
		}
		for (int i = 0; i < demand.size(); i++) {
			AuroraIntervalVector dvec = demand.get(i);
			sz = Math.min(du.length, dvec.size());
			for (int j = 0; j < dvec.size(); j++) {
				double ctr = dvec.get(j).getCenter();
				dvec.get(j).setCenter(ctr, 2*du[j]*ctr);
			}
		}
		return true;
	}
	
	/**
	 * Sets demand change frequency.<br>
	 * @param x demand sampling period.
	 * @return <code>true</code> if operation succeeded, <code>false</code> - otherwise.
	 */
	public synchronized boolean setDemandTP(double x) {
		if (x < myNetwork.getTP())
			return false;
		demandTP = x;
		return true;
	}
	
	/**
	 * Sets start time for demand profile.<br>
	 * @param x start time in hours.
	 * @return <code>true</code> if operation succeeded, <code>false</code> - otherwise.
	 */
	public synchronized boolean setDemandStartTime(double x) {
		if ((x < 0) || (x > 24))
			return false;
		demandST = x;
		return true;
	}
	
	/**
	 * Sets external demand value.
	 * @param x external demand value.
	 * @return <code>true</code> if operation succeeded, <code>false</code> - otherwise.
	 */
	public synchronized boolean setExternalDemandValue(AuroraIntervalVector x) {
		if (x == null) {
			extDemandVal = x;
			return true;
		}
		extDemandVal = new AuroraIntervalVector();
		extDemandVal.copy(x);
		extDemandVal.constraintLB(0);
		return true;
	}
	
	/**
	 * Sets capacity vector.<br>
	 * @param x capacity vector.
	 * @return <code>true</code> if operation succeeded, <code>false</code> - otherwise.
	 */
	public synchronized boolean setCapacityVector(Vector<AuroraInterval> x) {
		if (x == null)
			return false;
		capacity = x;
		for (int i = 0; i < capacity.size(); i++)
			capacity.get(i).constraintLB(0);
		return true;
	}
	
	/**
	 * Sets capacity vector from string.
	 * @param buf text buffer.
	 * @return <code>true</code> if operation succeeded, <code>false</code> - otherwise.
	 */
	public synchronized boolean setCapacityVector(String buf) {
		boolean res = true;
		if (buf == null)
			return false;
		StringTokenizer st = new StringTokenizer(buf, ", \t");
		capacity.clear();
		while (st.hasMoreTokens()) {
			AuroraInterval v = new AuroraInterval();
			v.setIntervalFromString(st.nextToken());
			v.constraintLB(0);
			capacity.add(v);
		}
		return res;
	}
	
	/**
	 * Sets capacity change frequency.
	 * @param x capacity sampling period.
	 * @return <code>true</code> if operation succeeded, <code>false</code> - otherwise.
	 */
	public synchronized boolean setCapacityTP(double x) {
		if (x < myNetwork.getTP())
			return false;
		capacityTP = x;
		return true;
	}
	
	/**
	 * Sets start time for capacity profile.<br>
	 * @param x start time in hours.
	 * @return <code>true</code> if operation succeeded, <code>false</code> - otherwise.
	 */
	public synchronized boolean setCapacityStartTime(double x) {
		if ((x < 0) || (x > 24))
			return false;
		capacityST = x;
		return true;
	}
	
	/**
	 * Sets external capacity value.
	 * @param x external capacity value.
	 * @return <code>true</code> if operation succeeded, <code>false</code> - otherwise.
	 */
	public synchronized boolean setExternalCapacityValue(AuroraInterval x) {
		if (x == null) {
			extCapVal = x;
			return true;
		}
		extCapVal = new AuroraInterval();
		extCapVal.copy(x);
		extCapVal.constraintLB(0);
		return true;
	}
	
	/**
	 * Resets VMT, VHT, Delay, Productivity Loss, In/Out Flow, Density and Speed sums.
	 */
	public synchronized void resetSums() {
		vht.setCenter(0, 0);
		vmt.setCenter(0, 0);
		delay.setCenter(0, 0);
		ploss.setCenter(0, 0);
		vmtSum.setCenter(0, 0);
		vhtSum.setCenter(0, 0);
		delaySum.setCenter(0, 0);
		plossSum.setCenter(0, 0);
		tsCount = 0;
		int sz = ((SimulationSettingsHWC)myNetwork.getContainer().getMySettings()).countVehicleTypes();
		densitySum = new AuroraIntervalVector(sz);
		inflowSum = new AuroraIntervalVector(sz);
		outflowSum = new AuroraIntervalVector(sz);
		speedSum = new AuroraInterval();
		resetAllSums = false;
		return;
	}
	
	/**
	 * Adjust vector data according to new vehicle weights.
	 * @param w array of weights.
	 * @return <code>true</code> if successful, <code>false</code> - otherwise.
	 */
	public synchronized boolean adjustWeightedData(double[] w) {
		double[] ow = ((SimulationSettingsHWC)myNetwork.getContainer().getMySettings()).getVehicleWeights();
		if ((w == null) || (ow == null))
			return false;
		boolean res = qSize.inverseAffineTransform(ow, 0);
		res &= density.inverseAffineTransform(ow, 0);
		res &= density0.inverseAffineTransform(ow, 0);
		res &= qSize.affineTransform(w, 0);
		res &= density.affineTransform(w, 0);
		res &= density0.affineTransform(w, 0);
		for (int i = 0; i < demand.size(); i++) {
			res &= demand.get(i).inverseAffineTransform(ow, 0);
			res &= demand.get(i).affineTransform(w, 0);
		}
		if (extDemandVal != null) {
			res &= extDemandVal.inverseAffineTransform(ow, 0);
			res &= extDemandVal.affineTransform(w, 0);
		}
		return res;
	}
	
	/**
	 * Copies data from the given Link to the current one.
	 * @param x given Network Element.
	 * @return <code>true</code> if successful, <code>false</code> - otherwise.
	 */
	public synchronized boolean copyData(AbstractNetworkElement x) {
		boolean res = super.copyData(x);
		if (res) {
			AbstractLinkHWC lnk = (AbstractLinkHWC)x;
			lanes = lnk.getLanes();
			myDynamics = lnk.getDynamics();
			setFD(lnk.getMaxFlow(), lnk.getCriticalDensity(), lnk.getJamDensity(), lnk.getCapacityDrop());
			density = (AuroraIntervalVector)lnk.getDensity();
			density0 = (AuroraIntervalVector)lnk.getInitialDensity();
			qMax = lnk.getQueueMax();
			setDemandKnobs(lnk.getDemandKnobs());
			demandTP = lnk.getDemandTP();
			demand = lnk.getDemandVector();
			capacityTP = lnk.getCapacityTP();
			capacity = lnk.getCapacityVector();
			terminalNode = lnk.getTerminalNode();
		}
		return res;
	}
	
	/**
	 * Overrides <code>java.lang.Object.toString()</code>.
	 * @return string that describes the Link.
	 */
	public String toString() {
		String buf = "";
		AbstractNode bn = getBeginNode();
		AbstractNode en = getEndNode();
		if (bn != null) 
			buf += bn.getName();
		else
			buf += "";
		buf += " > ";
		if (en != null)
			buf += en.getName();
		else
			buf += "";
		buf += " (" + Integer.toString(id) + ")";
		return buf;
	}
	
}
