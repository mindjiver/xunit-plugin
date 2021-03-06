package org.jenkinsci.plugins.xunit.service;

import com.google.inject.Inject;
import com.thalesgroup.dtkit.metrics.model.InputMetric;
import com.thalesgroup.dtkit.metrics.model.InputMetricXSL;
import com.thalesgroup.dtkit.util.converter.ConversionException;
import hudson.FilePath;
import org.jenkinsci.plugins.xunit.exception.XUnitException;
import org.jenkinsci.plugins.xunit.types.CustomInputMetric;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.List;


public class XUnitConversionService extends XUnitService implements Serializable {

    private XUnitLog xUnitLog;

    @Inject
    @SuppressWarnings("unused")
    void load(XUnitLog xUnitLog) {
        this.xUnitLog = xUnitLog;
    }

    /**
     * Converts the inputFile into a JUnit output file
     *
     * @param xUnitToolInfo        the xUnit info wrapper object
     * @param inputFile            the input file to be converted
     * @param workspace            the workspace
     * @param junitOutputDirectory the output parent directory that contains the JUnit output file
     * @return the converted file
     * @throws org.jenkinsci.plugins.xunit.exception.XUnitException
     *          an XUnitException is thrown if there is a conversion error.
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public File convert(XUnitToolInfo xUnitToolInfo, File inputFile, File workspace, File junitOutputDirectory) throws XUnitException {

        InputMetric inputMetric = xUnitToolInfo.getInputMetric();

        final String JUNIT_FILE_POSTFIX = ".xml";
        final String JUNIT_FILE_PREFIX = "TEST-";
        File parent = new File(junitOutputDirectory, inputMetric.getToolName());
        if (!parent.exists() && !parent.mkdirs()) {
            throw new XUnitException("Can't create " + parent);
        }
        File junitTargetFile = new File(parent, JUNIT_FILE_PREFIX + inputFile.hashCode() + JUNIT_FILE_POSTFIX);
        infoSystemLogger("Converting '" + inputFile + "' .");
        try {

            if (inputMetric instanceof CustomInputMetric) {
                return convertCustomInputMetric(xUnitToolInfo, inputFile, workspace, inputMetric, junitTargetFile);
            }

            if (inputMetric instanceof InputMetricXSL) {
                return convertInputMetricXSL(xUnitToolInfo, inputFile, inputMetric, junitTargetFile);
            }

            inputMetric.convert(inputFile, junitTargetFile);
            return junitTargetFile;

        } catch (ConversionException ce) {
            throw new XUnitException("Conversion error " + ce.getMessage(), ce);
        } catch (InterruptedException ie) {
            throw new XUnitException("Conversion error " + ie.getMessage(), ie);
        } catch (IOException ie) {
            throw new XUnitException("Conversion error " + ie.getMessage(), ie);
        }
    }

    private File convertCustomInputMetric(XUnitToolInfo xUnitToolInfo, File inputFile, File workspace, InputMetric inputMetric, File junitTargetFile) throws IOException, InterruptedException, XUnitException {
        CustomInputMetric customInputMetric = (CustomInputMetric) inputMetric;
        customInputMetric.setCustomXSLFile(new File(xUnitToolInfo.getCusXSLFile().getRemote()));
        inputMetric.convert(inputFile, junitTargetFile);
        return junitTargetFile;
    }

    private File convertInputMetricXSL(XUnitToolInfo xUnitToolInfo, File inputFile, InputMetric inputMetric, File junitTargetFile) throws IOException, InterruptedException {
        InputMetricXSL inputMetricXSL = (InputMetricXSL) inputMetric;
        FilePath userXSLFilePath = xUnitToolInfo.getUserContentRoot().child(inputMetricXSL.getUserContentXSLDirRelativePath());

        if (userXSLFilePath.exists()) {
            xUnitLog.infoConsoleLogger("Using the native embedded stylesheet in JENKINS_HOME.");
            try {
                return convertInputMetricXSLWithUserXSL(inputFile, junitTargetFile, inputMetricXSL, userXSLFilePath);
            } catch (XUnitException xe) {
                xUnitLog.errorConsoleLogger("Error occurs on the use of the user stylesheet: " + xe.getMessage());
                xUnitLog.infoConsoleLogger("Trying to use the native embedded stylesheet.");
                inputMetric.convert(inputFile, junitTargetFile);
                return junitTargetFile;
            }
        }

        inputMetric.convert(inputFile, junitTargetFile);
        return junitTargetFile;
    }

    private File convertInputMetricXSLWithUserXSL(File inputFile, File junitTargetFile, InputMetricXSL inputMetricXSL, FilePath userXSLFilePath) throws XUnitException {

        try {

            List<FilePath> filePathList = userXSLFilePath.list();
            if (filePathList.isEmpty()) {
                throw new XUnitException(String.format("There are no XSLs in '%s'", userXSLFilePath.getRemote()));
            }

            for (FilePath file : userXSLFilePath.list()) {
                if (!file.isDirectory()) {
                    inputMetricXSL.convert(inputFile, junitTargetFile, file.readToString(), null);
                    return junitTargetFile;
                }
            }

            throw new XUnitException(String.format("There are no XSLs in '%s'", userXSLFilePath.getRemote()));

        } catch (IOException e) {
            throw new XUnitException("Error in the use of the user stylesheet", e);
        } catch (InterruptedException e) {
            throw new XUnitException("Error in the use of the user stylesheet", e);
        }
    }

}
