package aurora.hwc.fdcalibration;

import java.io.*;
import java.util.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import aurora.*;
import aurora.hwc.*;
import aurora.service.*;

public class FDCalibrator {
	protected File configfile;
	protected HashMap<String,DataSource> datasourcemap = new HashMap<String,DataSource>();
	protected String outputfile;
	protected ContainerHWC mySystem = new ContainerHWC();
	protected HashMap <Integer,FiveMinuteData> data = new HashMap <Integer,FiveMinuteData> ();
	protected Vector<AbstractSensor> SensorList;
	
	protected Updatable updater = null;

	public FDCalibrator(String cfile, String ofile){
		configfile = new File(cfile);
		outputfile = ofile;
	}

	// execution .................................
	public void run() throws Exception{
		openfile();										// 1. read the original network file 
		readtrafficdata();								// 2. read pems 5 minute file
		for (int i = 0; i < SensorList.size(); i++) {			// 3. run calibration routine
			SensorLoopDetector S = (SensorLoopDetector) SensorList.get(i);
			if ((S.getVDS() != 0) && (S.getLink() != null))
				calibrate(S);
		}
		propagate();									// 4. extend parameters to the rest of the network
		export(); 										// 5. export to configuration file
	}

	// step 1
	private void openfile() throws Exception{
		if (configfile.getName().endsWith("xml")) { 
			String configURI = "file:" + configfile.getAbsolutePath();
			Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(configURI);
			mySystem.initFromDOM(doc.getChildNodes().item(0));
			if(!mySystem.validate())
				throw(new Exception("configuration file failed the validation test."));
		}
		else{
			throw(new Exception("invalid config file extension"));
		}
	}

	// step 2
	public void readtrafficdata() throws Exception{
		
		ArrayList<String> uniqueurls  = new ArrayList<String>();
		
		// construct list of stations to extract from datafile 
		SensorList = mySystem.getMyNetwork().getSensors();
		for(int i = 0; i < SensorList.size(); i++) {
			SensorLoopDetector S = (SensorLoopDetector) SensorList.get(i);
			if ((S.getVDS() != 0) && (S.getLink() != null)) {
				
				int thisvds = S.getVDS();
				ArrayList<Integer> thisvdslanes = new ArrayList<Integer>();
				for (int j = 1; j <= S.getLanes(); j++)
					thisvdslanes.add(j);		// THIS IS TEMPORARY, EVENTUALLY THE LOOP<->LANES MAP SHOULD BE SPECIFIED IN NE
				
				data.put(thisvds, new FiveMinuteData(thisvds));
				
				Vector<HistoricalDataSource> dsrc = S.getDataSources();
				for (int j = 0; j < dsrc.size(); j++) {
					String myurl =  dsrc.get(j).getURL();
					String myformat = dsrc.get(j).getFormat();
					if( uniqueurls.indexOf(myurl)<0 ){
						datasourcemap.put(myurl,new DataSource(myurl,myformat));
						uniqueurls.add(myurl);
					}
					DataSource thisdatasource = datasourcemap.get(myurl);
					thisdatasource.add_to_for_vds(thisvds);
					thisdatasource.add_to_for_vdslanes(thisvdslanes);
				}
			}
		}
		// Read 5 minute data to "data"
		PeMSClearinghouseInterpreter P = new PeMSClearinghouseInterpreter(datasourcemap);
		P.Read5minData(data, updater);
	}

	// step 3
	@SuppressWarnings("unchecked")
	public void calibrate(SensorLoopDetector S) {
		int i;
		int vds = S.getVDS();

		// output:
		float vf;
		float w;
		float q_max;
		float rj;
	    float rho_crit;
	    
		// nominal values
		float nom_vf = 65;
		float nom_w_min = 10;
		float nom_w_max = 19;
		float nom_w = 15;
		float nom_Qmax = 2000;

		// get data
		FiveMinuteData D = data.get(vds);
		int numdatapoints = D.time.size();

		// degenerate case
		if(numdatapoints==0){
			S.setFD(nom_vf,nom_w,nom_Qmax,nom_Qmax/nom_vf+nom_Qmax/nom_w,nom_Qmax/nom_vf);
			return;  
		}

		// organize into an array of DataPoint
		ArrayList<DataPoint> datavec = new ArrayList<DataPoint>();
		for(i=0;i<numdatapoints;i++)
			datavec.add(new DataPoint(D.dty.get(i),D.flw.get(i),D.spd.get(i)));
  
		// Find free-flow velocity ...............................

		// maximum flow and its corresponding density
		DataPoint maxflw = new DataPoint(0f,Float.NEGATIVE_INFINITY,0f);
		for(i=0;i<numdatapoints;i++)
			if(datavec.get(i).flw>maxflw.flw)
				maxflw.setval(datavec.get(i));

		q_max = maxflw.flw;

		// split data into congested and freeflow regimes ...............
		ArrayList<DataPoint> congestion = new ArrayList<DataPoint>();		// congestion states
		ArrayList<DataPoint> freeflow = new ArrayList<DataPoint>();			// freeflow states
		for(i=0;i<numdatapoints;i++)
			if(datavec.get(i).dty>=maxflw.dty)
				congestion.add(datavec.get(i));
			else
				freeflow.add(datavec.get(i));

		vf = percentile("spd",freeflow,0.5f);
		rho_crit = q_max/vf;

		// BINNING
		ArrayList<DataPoint> supercritical = new ArrayList<DataPoint>(); 	// data points above rho_crit
		for(i=0;i<numdatapoints;i++)
			if(datavec.get(i).dty>=rho_crit)
				supercritical.add(datavec.get(i));

		// sort supercritical w.r.t. density
		Collections.sort(supercritical);

		int numsupercritical = supercritical.size();
		int Bin_width = 10;
		int step=Bin_width;
		ArrayList<DataPoint> BinData = new ArrayList<DataPoint>();
		for(i=0;i<numsupercritical;i+=Bin_width){

			if(i+Bin_width>=numsupercritical)
		        step = numsupercritical-i;

		    if(step!=0){
		    	List<DataPoint> Bin = (List<DataPoint>) supercritical.subList(i,i+step);
		    	if(!Bin.isEmpty()){
			        float a = 2.5f*percentile("flw",Bin,0.75f) - 1.5f*percentile("flw",Bin,0.25f); 			        
			        float b = percentile("flw",Bin,1f);
			        BinData.add(new DataPoint(percentile("dty",Bin,0.5f),Math.min(a,b),Float.NaN));
		    	}
		    }
		}

		// Do constrained LS
		ArrayList<Float> ai = new ArrayList<Float>();
		ArrayList<Float> bi = new ArrayList<Float>();
		for(i=0;i<BinData.size();i++){
			bi.add(q_max - BinData.get(i).flw);
			ai.add(BinData.get(i).dty - rho_crit);
		}

		if(BinData.size()>0){
			float sumaibi = 0;
			float sumaiai = 0;
			for(i=0;i<BinData.size();i++){
				sumaibi += ai.get(i)*bi.get(i);
				sumaiai += ai.get(i)*ai.get(i);
			}
			w = sumaibi/sumaiai;
			w = Math.max(w,nom_w_min);
			w = Math.min(w,nom_w_max);
		    rj = q_max / w + rho_crit;
		}
		else{
		    w  = nom_w;
		    rj = q_max*(vf + nom_w)/(vf*nom_w);
		}

		// store parameters in sensor
		S.setFD(vf,w,q_max,rj,rho_crit);
	}
  
	// step 4
	public void propagate(){
		int i;
		boolean done = false;

		// populate the grow network
		ArrayList<GrowLink> arraygrownetwork = new ArrayList<GrowLink>();
		HashMap<Integer,GrowLink> hashgrownetwork = new HashMap<Integer,GrowLink>();
		Vector<AbstractLink> LinkList = mySystem.getMyNetwork().getLinks();
		for(i=0;i<LinkList.size();i++){
			AbstractLink L = LinkList.get(i);
			GrowLink G = new GrowLink(L);
			hashgrownetwork.put(L.getId(),G);
			arraygrownetwork.add(G);
		}

		// initialize the grow network with sensored links 
		for(i=0;i<SensorList.size();i++){
			SensorLoopDetector S = (SensorLoopDetector) SensorList.get(i);
			if(S.getVDS()!=0 & S.getLink()!=null){
				int linkid = S.getLink().getId();
				GrowLink G = hashgrownetwork.get(linkid);
				G.sensor = S;
				G.isassigned = true;
				G.isgrowable = true;
			}
		}

		// repeatedly traverse network until all assigned links cannot be grown
		while(!done){
			done = true;

			// step through all links
			for(i=0;i<arraygrownetwork.size();i++) {

				GrowLink G = arraygrownetwork.get(i);

				// continue if G is assigned and not growable, or if G is unassigned
				if( (G.isassigned & !G.isgrowable) | !G.isassigned)
					continue;

				done = false;

				// so G is assigned and growable, expand to its upstream and downstream links
				growout("up",G,hashgrownetwork);
				growout("dn",G,hashgrownetwork);
				G.isgrowable = false;
			}
		}

		// copy parameters to the links
		for(i=0;i<arraygrownetwork.size();i++){
			GrowLink G = arraygrownetwork.get(i);
			if(G.isassigned)
				((AbstractLinkHWC) G.link).setFD(G.sensor.q_max,G.sensor.rho_crit,G.sensor.rj,0.0);
		}

	}

	// step 5
	private void export() throws Exception{
		PrintStream oos = new PrintStream(new FileOutputStream(outputfile));
		mySystem.xmlDump(oos);
		oos.close();
	}
	
	// routines called from service.....................
	
	public synchronized void setMySystem(ContainerHWC my_sys) {
		mySystem = my_sys;
		return;
	}
	
	public synchronized void setUpdater(Updatable upd) {
		updater = upd;
		return;
	}

	// private routines.................................

	// compute the p'th percentile qty (p in [0,1])
	private float percentile(String qty,List<DataPoint> x,float p){
		ArrayList<Float> values = new ArrayList<Float>();
		int numdata = x.size();
		if(qty.equals("spd"))
			for(int i=0;i<numdata;i++)
				values.add(x.get(i).spd);
		if(qty.equals("flw"))
			for(int i=0;i<numdata;i++)
				values.add(x.get(i).flw);
		if(qty.equals("dty"))
			for(int i=0;i<numdata;i++)
				values.add(x.get(i).dty);

		Collections.sort(values);

		if(p==0)
			return values.get(0);
		if(p==1)
			return values.get(numdata-1);

		int z = (int) Math.floor(numdata*p);
		if(numdata*p==z)
			return (values.get(z-1)+values.get(z))/2f;
		else
			return values.get(z);
	}

	private void growout(String upordn,GrowLink G,HashMap<Integer,GrowLink> H){
		AbstractNode node;
		Vector<AbstractNetworkElement> newlinks = null;
		if(upordn.equals("up")){			// grow in upstream direction
			node = G.link.getBeginNode();
			if(node!=null)
				newlinks = node.getPredecessors();
		}
		else{								// grow in downstream direction
			node = G.link.getEndNode();
			if(node!=null)
				newlinks = node.getSuccessors();
		}

		if(newlinks==null)
			return;

		for(int i=0;i<newlinks.size();i++){
			GrowLink nG = H.get( ((AbstractLink) newlinks.get(i)).getId() );
			// propagate if nG is unassigned and of the same type as G
			if(!nG.isassigned & nG.link.getType()==G.link.getType()){
				nG.sensor = G.sensor;
				nG.isassigned = true;
				nG.isgrowable = true;
			}
		}
	}

	// internal classes ...............................
	public class DataPoint implements Comparable {
		float dty;
		float flw;
		float spd;
		public DataPoint(float d,float f,float s){
			dty=d;
			flw=f;
			spd=s;
		}
		public void setval(DataPoint x){
			this.dty=x.dty;
			this.flw=x.flw;
			this.spd=x.spd;
		}
		@Override
		public String toString() {
			return String.format("%f",dty);
		}
		@Override
		public int compareTo(Object x) {
			float thatdty = ((DataPoint) x).dty;
			if(this.dty==thatdty)
				return 0;
			else if(this.dty>thatdty)
				return 1;
			else
				return -1;
		}
	}

	public class GrowLink {
		public AbstractLink link;
		public SensorLoopDetector sensor=null;
		public boolean isgrowable = false; // link possibly connected to unassigned links
		public boolean isassigned = false; // network is divided into assigned and unassigned subnetworks
		public GrowLink(AbstractLink l){link=l;}
	}

	// main function .................................
	public static void main(String[] args) {

		try {
			String configfilename,outputfilename;
			if(args.length < 2){
				System.out.println("Arguments:");
				System.out.println("1) Configuration file name.");
				System.out.println("2) Output file name.");
				return;
			}
			configfilename = args[0];
			outputfilename = args[1];

			// basic checks
			if(!configfilename.endsWith(".xml")) {
				System.out.println("Invalid configuration file extension.");
				return;
			}

			if (!outputfilename.endsWith(".xml")) {
				System.out.println("Invalid output file extension.");
				return;
			}
			FDCalibrator calibrator = new FDCalibrator(configfilename, outputfilename);
			calibrator.run();
		}
		catch(Exception e){
			System.out.println(e);
			System.exit(1);
		}
		System.out.println("Done!");
		System.exit(0);
	}

}
