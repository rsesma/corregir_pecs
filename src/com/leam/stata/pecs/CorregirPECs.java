package com.leam.stata.pecs;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;

import com.itextpdf.text.pdf.AcroFields;
import com.itextpdf.text.pdf.PdfReader;

import com.stata.sfi.*;


public class CorregirPECs {

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
	
	private static final String C_ARXIUS_DESCOMPRIMITS = "Arxius descomprimits a";
	private static final String C_CARPETA_PDFS_JA_EXISTEIX = "La carpeta pdfs ja existeix; no cal descomprimir";
	
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
				
				CheckPECs(destDir.getAbsolutePath());
			} else {
				SFIToolkit.displayln(C_CARPETA_PDFS_JA_EXISTEIX + " (" + destDir.getAbsolutePath() + ")");
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
    private static int CheckPECs(String pecsPath) {
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
		        	lproblems = !ext.equalsIgnoreCase("pdf"); 
		        	if (!lproblems) {
		                PdfReader reader = new PdfReader(f.getAbsolutePath());
		                AcroFields form = reader.getAcroFields();
		                int nsize = form.getFields().size();
		                reader.close();
		                // fields?		                
		                lproblems = (nsize==0);
		                
		        	} 
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
		//File pdfs = new File(new File(args[0]).getParentFile().getAbsolutePath(),C_PDFS);
		
		GetPlantilla(args[1]);
				
/*		Boolean first = true;
		
    	try {
		    for (File f : pdfs.listFiles()) {
				// loop through not hidden files
		        if (f.isFile() && !f.isHidden()) {
		        	if (first) {
		        		
		                PdfReader reader = new PdfReader(f.getAbsolutePath());
		                AcroFields form = reader.getAcroFields();

		                Set<String> fldNames = form.getFields().keySet();
		                for (String fldName : fldNames) {
		                	SFIToolkit.displayln(fldName);
//		                  System.out.println( fldName + ": " + fields.getField( fldName ) );
		                }                		                
		        	}
		        	first = false;
		        	break;
		        }
		    }			
    	} catch (Exception e) {
			SFIToolkit.errorln(C_ERROR_OBRINT_PECS);
			return(198);
	    }*/
		
		return(0);
	}
	
	private static int GetPlantilla(String dirSol) {
		File f = new File(dirSol);
		
		if (f.exists()) {
			try {
				LineIterator it = FileUtils.lineIterator(f, "UTF-8");
		    	try {
		    		it.nextLine();		// first line has field names, not useful
		    	    while (it.hasNext()) {
		    	    	SFIToolkit.displayln(it.nextLine());
		    	    }
		    	} catch (Exception e) {
					SFIToolkit.errorln(C_ERROR_OBRINT_SOL);
					return(198);
		        } finally {
		    	    it.close();
		    	}
		    } catch (Exception e) {
				SFIToolkit.errorln(C_ERROR_OBRINT_SOL);
				return(198);
		    }
		} else {
			SFIToolkit.errorln(C_ERROR_ARXIU_SOL);
			return(198);
		}
		
		return(0);
	}

}
