package com.leam.stata.pecs;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;

import com.itextpdf.text.pdf.AcroFields;
import com.itextpdf.text.pdf.PdfReader;

import com.stata.sfi.*;


public class CorregirPECs {
	
	private static ArrayList<Pregunta> Plantilla = null;

	private static final int BUFFER_SIZE = 4096;		// size of the buffer to read/write data to unzip
	
	private static final String C_PDFS = "pdfs";
	private static final String C_PROBLEMES = "problemes";
	private static final String C_ATENCIO = "ATENCIÓ!";
	private static final String C_HI_HA_PROBLEMES = "Hi ha PECs amb problemes; veure la carpeta";

	private static final String C_ERROR_DESCOMPRIMIR_ZIP = "no és possible descomprimir l'arxiu zip";
	private static final String C_ERROR_ARXIU_ZIP = "arxiu zip no vàlid";
	private static final String C_ERROR_ARXIU_SOL = "arxiu solució no vàlid";
	private static final String C_ERROR_CHECK_PECS = "error comprovant les PECs";
	private static final String C_ERROR_OBRINT_SOL = "error obrint l'arxiu solució";
	private static final String C_ERROR_OBRINT_PECS = "error obrint arxiu PEC";

	private static final String C_ARXIUS_DESCOMPRIMITS = "Arxius descomprimits a";
	private static final String C_CARPETA_PDFS_JA_EXISTEIX = "La carpeta pdfs ja existeix";
	private static final String C_NO_CAL_DESCOMPRIMIR = "no cal descomprimir";
	
    /**
     * Extracts a zip file specified by args[0] to a directory 
     * /pdfs (will be created if does not exists) besides the zip file 
     * @param args[]
     */
	public static int extractZIP(String args[]) {

		String dirZIP = args[0];		// argument passed from Stata
		
		File fzip = new File(dirZIP);
		if (fzip.exists() && !fzip.isDirectory()) {
			File destDir = new File(fzip.getParentFile().getAbsolutePath(),C_PDFS);
			
			if (!destDir.exists()) {
				destDir.mkdir();			// make pdfs dir
				
				try {
			        ZipInputStream zipIn = new ZipInputStream(new FileInputStream(dirZIP));
			        ZipEntry entry = zipIn.getNextEntry();
			        // iterates over entries in the zip file
			        while (entry != null) {
			            String filePath = destDir.getAbsolutePath() + File.separator + entry.getName();
			            if (!entry.isDirectory()) {
			                // if the entry is a file, extracts it
			                extractFile(zipIn, filePath);
			            }
			            zipIn.closeEntry();
			            entry = zipIn.getNextEntry();
			        }
			        zipIn.close();
				} catch (Exception e) {
					SFIToolkit.errorln(C_ERROR_DESCOMPRIMIR_ZIP);
					return(198);
				}
				SFIToolkit.displayln(C_ARXIUS_DESCOMPRIMITS + ": " + destDir.getAbsolutePath());
				
				checkPECs(destDir.getAbsolutePath());
			} else {
				SFIToolkit.displayln(C_CARPETA_PDFS_JA_EXISTEIX + " (" + destDir.getAbsolutePath() + "); " + C_NO_CAL_DESCOMPRIMIR);
			}
		} else {
			SFIToolkit.errorln(C_ERROR_ARXIU_ZIP);
			return(198);
		}
		
		return(0);
	}
    /**
     * Extracts a zip entry (file entry)
     * @param zipIn
     * @param filePath
     */
    private static int extractFile(ZipInputStream zipIn, String filePath) {
		try {
	        BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(filePath));
	        byte[] bytesIn = new byte[BUFFER_SIZE];
	        int read = 0;
	        while ((read = zipIn.read(bytesIn)) != -1) {
	            bos.write(bytesIn, 0, read);
	        }
	        bos.close();
		} catch (Exception e) {
			SFIToolkit.errorln(C_ERROR_DESCOMPRIMIR_ZIP);
			return(198);
		}
		return(0);
    }
    /**
     * Checks the files of dirPath as correct PECs
     * @param pecsPath
     */
    private static int checkPECs(String pecsPath) {
        Boolean lproblems = false;
        File folder = new File(pecsPath);					// PECs folder
        File problems = new File(pecsPath,C_PROBLEMES);		// folder for problem files
        if (!problems.exists()) problems.mkdir();			// make pdfs dir
        
    	try {
		    for (File f : folder.listFiles()) {
				// loop through not hidden files
		        if (f.isFile() && !f.isHidden()) {
		        	String name = f.getName();
		        	String ext = name.substring(name.lastIndexOf(".")+1);
		        	// PDF?
		        	if (ext.equalsIgnoreCase("pdf")) {
		                PdfReader reader = new PdfReader(f.getAbsolutePath());
		                AcroFields form = reader.getAcroFields();
		                int nsize = form.getFields().size();
		                reader.close();
		                // fields?		                
		                if (nsize==0) lproblems = true;
		        	} else { lproblems = true; }
		        	// if problems, move to problems dir
		        	if (lproblems) f.renameTo(new File(problems.getAbsolutePath(),f.getName()));
		        }
		    }			
		    if (lproblems) {
				SFIToolkit.error(C_ATENCIO + ": ");
				SFIToolkit.displayln(C_HI_HA_PROBLEMES + " " + problems.getAbsolutePath());
			}
    	} catch (Exception e) {
			SFIToolkit.errorln(C_ERROR_CHECK_PECS);
			return(198);
	    }
		return(0);
	}
    
    /**
     * Get PEC data from the directory pdfs besides the zip file for args[0] 
     * @param args[]
     */
	public static int getPECData(String args[]) {

		// get PEC data from txt
		int rc = getPlantilla(args[1]);
		if (rc!=0) return(rc);

	    File pec_data = new File(new File(args[0]).getParentFile().getAbsolutePath(),"pec_data.dta");
	    if (!pec_data.exists()) {
			long obs = 0;
			
			// create variables
	        rc = Data.addVarStr("DNI", 11);
	        if (rc!=0) return(rc);
	        rc = Data.addVarStr("Pregunta", 10);
	        if (rc!=0) return(rc);
	        rc = Data.addVarStr("Respostes", 20);
	        if (rc!=0) return(rc);
			
	        File pdfs = new File(new File(args[0]).getParentFile().getAbsolutePath(),C_PDFS);
	    	try {
			    for (File f : pdfs.listFiles()) {
					// loop through not hidden files
			        if (f.isFile() && !f.isHidden()) {
		                String n = f.getName();
		                String dni = n.substring(n.lastIndexOf("_")+1);
		                dni = dni.substring(0,dni.indexOf("."));
	
		                // open PEC
	                    PdfReader reader = new PdfReader(f.getAbsolutePath());
	                    AcroFields form = reader.getAcroFields();
	                    if (form.getFields().size()>0) {
	                        // loop to get field data
	                        for (Pregunta p : Plantilla) {
	                            // Add PEC data
	                            obs++;
	                            rc = Data.setObsTotal(obs);
	                            if (rc!=0) return(rc);
	                            
	                            rc = Data.storeStr(1, obs, dni);
	                            if(rc!=0) return(rc);
	                            rc = Data.storeStr(2, obs, p.nom);
	                            if(rc!=0) return(rc);
	                            rc = Data.storeStr(3, obs, form.getField(p.nom).replace(",", "."));
	                            if(rc!=0) return(rc);
	                        }
	                        reader.close();
	                    }
			        }
			    }
			    
			    SFIToolkit.executeCommand("qui save " + pec_data.getAbsolutePath() + ", replace", false);
			    SFIToolkit.displayln("PECs importades");
	    	} catch (Exception e) {
				SFIToolkit.errorln(C_ERROR_OBRINT_PECS);
				return(198);
		    }
	    } else { SFIToolkit.displayln("L'arxiu pec_data ja existeix; no cal extreure les dades de les PECs"); }
		    
	    File pec_freq = new File(new File(args[0]).getParentFile().getAbsolutePath(),"pec_freq.dta");
	    if (!pec_freq.exists()) {
		    SFIToolkit.executeCommand("pecs freq", false);
		    SFIToolkit.executeCommand("qui save " + pec_freq.getAbsolutePath() + ", replace", false);
		    SFIToolkit.displayln("Freqüències calculades");
	    }  else { SFIToolkit.displayln("L'arxiu pec_freq ja existeix; no cal calcular les freqüències"); }
		
		return(0);
	}
	
	private static int getPlantilla(String dirSol) {
		File f = new File(dirSol);
		Plantilla = new ArrayList<Pregunta>();
		
		if (f.exists()) {
			try {
				LineIterator it = FileUtils.lineIterator(f, "UTF-8");
		    	try {
		    		it.nextLine();		// first line has field names, not useful
		    	    while (it.hasNext()) {
		    	    	Plantilla.add(new Pregunta(it.nextLine()));
		    	    }
		    	} catch (Exception e) {
					SFIToolkit.errorln(C_ERROR_OBRINT_SOL + ": " + e.getMessage());
					return(198);
		        } finally {
		    	    it.close();
		    	}
		    } catch (Exception e) {
				SFIToolkit.errorln(C_ERROR_OBRINT_SOL + ": " + e.getMessage());
				return(198);
		    }
		} else {
			SFIToolkit.errorln(C_ERROR_ARXIU_SOL);
			return(198);
		}
		
		return(0);
	}
	
    /**
     * Analyze PEC data 
     * @param args[]
     */
	public static int Analyze(String args[]) {
		
		int rc = getPlantilla(args[0]);
		if (rc!=0) return(rc);
		
		File pec_freq = new File(new File(args[0]).getParentFile().getAbsolutePath(),"pec_freq.dta");
		SFIToolkit.executeCommand("qui use " + pec_freq.getAbsolutePath() + ", clear", false);
		for (Pregunta p : Plantilla) {
			SFIToolkit.executeCommand("qui preserve", false);
			SFIToolkit.executeCommand("qui keep *" + p.nom, false);
			SFIToolkit.executeCommand("qui drop c" + p.nom, false);
			SFIToolkit.executeCommand("qui rename " + p.nom + " value", false);
			SFIToolkit.executeCommand("qui rename p" + p.nom + " percent", false);
			SFIToolkit.executeCommand("qui drop if missing(percent)", false);
			SFIToolkit.executeCommand("gsort -percent", false);
			SFIToolkit.executeCommand("list, clean", false);
			SFIToolkit.executeCommand("qui restore", false);
		}
		
		return(0);
	}


	public static void main(String args[]) {
/*		String dirZIP = "C:\\Users\\tempo\\Desktop\\CorregirPECs\\PEC4_DE0\\PECS_DE0_2017-18.zip";
		String dirSol = "C:\\Users\\tempo\\Desktop\\CorregirPECs\\PEC4_DE0\\sol.txt";
		String params[] = {dirZIP, dirSol};
		getPECData(params);*/
	}
}
