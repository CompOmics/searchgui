package eu.isas.searchgui.processbuilders;

import com.compomics.util.exceptions.ExceptionHandler;
import com.compomics.util.experiment.identification.Advocate;
import com.compomics.util.io.IoUtil;
import com.compomics.util.parameters.identification.search.SearchParameters;
import com.compomics.util.parameters.identification.tool_specific.TideParameters;
import com.compomics.util.waiting.WaitingHandler;
import java.io.File;
import java.io.IOException;

/**
 * ProcessBuilder for the Tide search command.
 *
 * @author Harald Barsnes
 * @author Marc Vaudel
 */
public class TideSearchProcessBuilder extends SearchGUIProcessBuilder {

    /**
     * The name of the Tide executable.
     */
    public static final String EXECUTABLE_FILE_NAME = "crux";
    /**
     * The temp folder for Tide files.
     */
    private File tideTempFolder;
    /**
     * The spectrum file.
     */
    private File spectrumFile;
    /**
     * The advanced Tide parameters.
     */
    private TideParameters tideParameters;

    /**
     * Constructor.
     *
     * @param tideFolder the Tide folder
     * @param tideTempFolder the folder for Tide temp files
     * @param tideIndexLocation the file where to store the index
     * @param searchParameters the search parameters
     * @param spectrumFile the spectrum file
     * @param waitingHandler the waiting handler
     * @param exceptionHandler the handler of exceptions
     * @param nThreads the number of threads to use
     *
     * @throws IOException thrown of there are problems creating the Tide
     * parameter file
     */
    public TideSearchProcessBuilder(
            File tideFolder,
            File tideTempFolder,
            File tideIndexLocation,
            SearchParameters searchParameters,
            File spectrumFile,
            WaitingHandler waitingHandler,
            ExceptionHandler exceptionHandler,
            int nThreads
    ) throws IOException {
        
        ///////////////////////////////////////////////////
        // the following Tide options are not implemented:
        //  --use-tailor-calibration
        //  --mod-precision
        //  --auto-precursor-window
        //  --auto-mz-bin-width
        //  --deisotope
        //  --isotope-error
        //  --score-function
        //  --fragment-tolerance
        //  --evidence-granularity
        //  --skip-preprocessing
        //  --mass-precision
        //  --precision
        //  --use-z-line
        //  --peptide-centric-search
        //  --scan-number
        //
        //  see http://crux.ms/commands/tide-search.html
        ///////////////////////////////////////////////////

        this.waitingHandler = waitingHandler;
        this.exceptionHandler = exceptionHandler;
        this.tideTempFolder = tideTempFolder;
        tideParameters = (TideParameters) searchParameters.getIdentificationAlgorithmParameter(Advocate.tide.getIndex());
        this.spectrumFile = spectrumFile;

        // make sure that the file is executable
        File tide = new File(tideFolder.getAbsolutePath() + File.separator + EXECUTABLE_FILE_NAME);
        tide.setExecutable(true);

        // full path to executable
        process_name_array.add(tide.getAbsolutePath());
        process_name_array.add("tide-search");

        // compute sp cores
        process_name_array.add("--compute-sp");
        if (tideParameters.getComputeSpScore()) {
            process_name_array.add("T");
        } else {
            process_name_array.add("F");
        }

        // link to the spectrum file
        process_name_array.add(spectrumFile.getAbsolutePath());

        // link to the index
        File tideIndexFolder = tideIndexLocation != null ? tideIndexLocation : new File(tideTempFolder, tideParameters.getFastIndexFolderName());
        process_name_array.add(tideIndexFolder.getAbsolutePath());

        // overwrite existing files
        process_name_array.add("--overwrite");
        process_name_array.add("T");

        // the number of matches per spectrum
        process_name_array.add("--top-match");
        process_name_array.add(tideParameters.getNumberOfSpectrumMatches().toString());

        // precursor accuracy
        process_name_array.add("--precursor-window");
        process_name_array.add("" + searchParameters.getPrecursorAccuracy());
        process_name_array.add("--precursor-window-type");
        if (searchParameters.getPrecursorAccuracyType() == SearchParameters.MassAccuracyType.DA) {
            process_name_array.add("mz"); // @TODO: what about mass?
        } else {
            process_name_array.add("ppm");
        }

        // add the mgf file to the result file name
        process_name_array.add("--fileroot");
        process_name_array.add(IoUtil.removeExtension(spectrumFile.getName()));

        // calculate p-values
        process_name_array.add("--exact-p-value");
        if (tideParameters.getComputeExactPValues()) {
            process_name_array.add("T");
        } else {
            process_name_array.add("F");
        }

        // set the output directory
        process_name_array.add("--output-dir");
        process_name_array.add(new File(tideTempFolder, tideParameters.getOutputFolderName()).getAbsolutePath());

        // min spectrum mz
        process_name_array.add("--spectrum-min-mz");
        process_name_array.add(tideParameters.getMinSpectrumMz().toString());

        // max spectrum mz
        if (tideParameters.getMaxSpectrumMz() != null) {
            process_name_array.add("--spectrum-max-mz");
            process_name_array.add(tideParameters.getMaxSpectrumMz().toString());
        }

        // min peaks in spectrum
        process_name_array.add("--min-peaks");
        process_name_array.add(tideParameters.getMinSpectrumPeaks().toString());

        // max precursor charge
        process_name_array.add("--max-precursor-charge");
        process_name_array.add("" + searchParameters.getMaxChargeSearched());

        // remove precusor peak
        process_name_array.add("--remove-precursor-peak");
        if (tideParameters.getRemovePrecursor()) {
            process_name_array.add("T");
        } else {
            process_name_array.add("F");
        }

        // remove precusor peak tolerance
        process_name_array.add("--remove-precursor-tolerance");
        process_name_array.add(tideParameters.getRemovePrecursorTolerance().toString());

        // print search progress
        process_name_array.add("--print-search-progress");
        process_name_array.add(tideParameters.getPrintProgressIndicatorSize().toString());

        // use flanking peaks
        process_name_array.add("--use-flanking-peaks");
        if (tideParameters.getUseFlankingPeaks()) {
            process_name_array.add("T");
        } else {
            process_name_array.add("F");
        }

        // use neutral loss peaks
        process_name_array.add("--use-neutral-loss-peaks");
        if (tideParameters.getUseNeutralLossPeaks()) {
            process_name_array.add("T");
        } else {
            process_name_array.add("F");
        }

        // mz bin width
        process_name_array.add("--mz-bin-width");
        process_name_array.add(tideParameters.getMzBinWidth().toString());

        // mz bin offset
        process_name_array.add("--mz-bin-offset");
        process_name_array.add(tideParameters.getMzBinOffset().toString());

        // number of threads
        process_name_array.add("--num-threads");
        process_name_array.add("" + nThreads);

        // concatinate target and decoy results
        process_name_array.add("--concat");
        if (tideParameters.getConcatenatTargetDecoy()) {
            process_name_array.add("T");
        } else {
            process_name_array.add("F");
        }

        // store spectra
        if (tideParameters.getStoreSpectraFileName() != null) {
            process_name_array.add("--store-spectra");
            process_name_array.add(tideParameters.getStoreSpectraFileName());
        }
//        
//        // store index
//        if (tideParameters.getStoreSpectraFileName() != null) { // @TODO: implement?
//            process_name_array.add("--store-index");
//            process_name_array.add(tideParameters.getStoreIndex());
//        }
//
//        // use z line
//        if (tideParameters.getUseZLine()) { // @TODO: implement?
//            process_name_array.add("T");
//        } else {
//            process_name_array.add("F");
//        }

        // text output
        process_name_array.add("--txt-output");
        if (tideParameters.getTextOutput()) {
            process_name_array.add("T");
        } else {
            process_name_array.add("F");
        }

        // SQT output
        process_name_array.add("--sqt-output");
        if (tideParameters.getSqtOutput()) {
            process_name_array.add("T");
        } else {
            process_name_array.add("F");
        }

        // pepxml output
        process_name_array.add("--pepxml-output");
        if (tideParameters.getPepXmlOutput()) {
            process_name_array.add("T");
        } else {
            process_name_array.add("F");
        }

        // mzid output
        process_name_array.add("--mzid-output");
        if (tideParameters.getMzidOutput()) {
            process_name_array.add("T");
        } else {
            process_name_array.add("F");
        }

        // precolator input file output
        process_name_array.add("--pin-output");
        if (tideParameters.getPinOutput()) {
            process_name_array.add("T");
        } else {
            process_name_array.add("F");
        }

        // use parameter file
        //process_name_array.add("--parameter-file");
        //process_name_array.add("a file"); // @TODO: implement?
        //
        // output verbosity
        process_name_array.add("--verbosity");
        process_name_array.add(tideParameters.getVerbosity().toString());

        process_name_array.trimToSize();

        // print the command to the log file
        System.out.println(System.getProperty("line.separator") + System.getProperty("line.separator") + "tide search command: ");

        for (Object currentElement : process_name_array) {
            System.out.print(currentElement + " ");
        }

        System.out.println(System.getProperty("line.separator"));

        pb = new ProcessBuilder(process_name_array);
        pb.directory(tideFolder);

        // set error out and std out to same stream
        pb.redirectErrorStream(true);
    }

    @Override
    public String getType() {
        return "Tide Searching";
    }

    @Override
    public String getCurrentlyProcessedFileName() {
        return spectrumFile.getName();
    }
}
