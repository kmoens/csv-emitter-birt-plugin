package org.eclipse.birt.report.engine.emitter.csv;

import java.io.IOException;
import java.io.OutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.birt.core.exception.BirtException;
import org.eclipse.birt.report.engine.api.EngineException;
import org.eclipse.birt.report.engine.api.IRenderOption;
import org.eclipse.birt.report.engine.content.IBandContent;
import org.eclipse.birt.report.engine.content.ICellContent;
import org.eclipse.birt.report.engine.content.IDataContent;
import org.eclipse.birt.report.engine.content.IElement;
import org.eclipse.birt.report.engine.content.ILabelContent;
import org.eclipse.birt.report.engine.content.IPageContent;
import org.eclipse.birt.report.engine.content.IReportContent;
import org.eclipse.birt.report.engine.content.IRowContent;
import org.eclipse.birt.report.engine.content.IStyle;
import org.eclipse.birt.report.engine.content.ITableContent;
import org.eclipse.birt.report.engine.content.ITextContent;
import org.eclipse.birt.report.engine.css.engine.value.birt.BIRTConstants;
import org.eclipse.birt.report.engine.emitter.ContentEmitterAdapter;
import org.eclipse.birt.report.engine.emitter.EmitterUtil;
import org.eclipse.birt.report.engine.emitter.IEmitterServices;
import org.eclipse.birt.report.engine.ir.EngineIRConstants;
import org.eclipse.birt.report.engine.presentation.ContentEmitterVisitor;

public class CSVReportEmitter extends ContentEmitterAdapter {
    protected static Logger logger = Logger.getLogger(CSVReportEmitter.class.getName());

    protected static final String OUTPUT_FORMAT_CSV = "csv";
    protected static final String REPORT_FILE = "report.csv";

    protected ContentEmitterVisitor contentVisitor;
    protected IEmitterServices services;

    protected CSVWriter writer;

    protected IReportContent report;
    protected IRenderOption renderOption;

    protected int totalColumns;
    protected int currentColumn;

    protected OutputStream out = null;

    protected boolean isFirstPage = false;
    protected long firstTableID = -1;

    protected boolean writeData = true;
    protected String tableToOutput;
    protected boolean outputCurrentTable;
    protected String delimiter = null;

    public CSVReportEmitter() {
        contentVisitor = new ContentEmitterVisitor(this);
    }

    /*
     * (non-Javadoc)
     *
     * @see org.eclipse.birt.report.engine.emitter.ContentEmitterAdapter#initialize(org.eclipse.birt.report.engine.emitter.IEmitterServices)
     */
    @Override
    public void initialize(final IEmitterServices services) throws EngineException {
        this.services = services;
        out = EmitterUtil.getOuputStream(services, REPORT_FILE);
    }

    @Override
    public void start(final IReportContent report) {
        logger.log(Level.FINE, "Starting CSVReportEmitter.");

        this.report = report;

        renderOption = report.getReportContext().getRenderOption();

        tableToOutput = (String) renderOption.getOption(ICSVRenderOption.EXPORT_TABLE_BY_NAME);

        // Setting tableToOutput to Default as user has not set any Render Option to Output a specific Table
        if (tableToOutput == null) {
            tableToOutput = "Default";
        }

        delimiter = (String) renderOption.getOption(ICSVRenderOption.DELIMITER);

        // Setting Default Field Delimiter if user has not specified any Delimiter
        if (delimiter == null) {
            delimiter = CSVTags.SEMICOLON;
        }

        writer = new CSVWriter(CSVTags.QUOTE, delimiter);
        writer.open(out, "UTF-8");
    }

    @Override
    public void end(final IReportContent report) {
        logger.log(Level.FINE, "CSVReportEmitter end report.");
        writer.close();

        // Informing user if Table Name provided in Render Option is not found and Blank Report is getting generated
        if (tableToOutput != "Default" && report.getDesign().getReportDesign().findElement(tableToOutput) == null) {
            System.out.println(tableToOutput + " Table not found in Report Design. Blank Report Generated!!");
            logger.log(Level.WARNING, tableToOutput + " Table not found in Report Design. Blank Report Generated!!");
        }
        if (out != null) {
            try {
                out.close();
            } catch (IOException e) {
                logger.log(Level.WARNING, e.getMessage(), e);
            }
        }
    }

    @Override
    public void startPage(final IPageContent page) throws BirtException {
        logger.log(Level.FINE, "CSVReportEmitter startPage");

        startContainer(page);

        if (page.getPageNumber() > 1) {
            isFirstPage = false;
        } else {
            isFirstPage = true;
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see org.eclipse.birt.report.engine.emitter.ContentEmitterAdapter#startLabel(org.eclipse.birt.report.engine.content.ILabelContent) To avoid framework to print
     * label for every page.
     */
    @Override
    public void startLabel(final ILabelContent label) throws BirtException {
        if (isFirstPage) {
            startText(label);
        }
    }

    @Override
    public void startTable(final ITableContent table) {
        assert table != null;
        totalColumns = table.getColumnCount();

        if (firstTableID == -1) {
            firstTableID = table.getInstanceID().getComponentID();
        }

        String currentTableName = table.getName();

        if (tableToOutput.equals("Default") && table.getInstanceID().getComponentID() == firstTableID) {
            outputCurrentTable = true;
        } else if (currentTableName != null && currentTableName.equals(tableToOutput)) {
            outputCurrentTable = true;
        } else {
            outputCurrentTable = false;
        }
    }

    @Override
    public void startRow(final IRowContent row) {
        assert row != null;

        if (isRowInFooter(row) || isRowInHeaderExceptFirstHeader(row) || outputCurrentTable != true) {
            writeData = false;
        }

        currentColumn = 0;
    }

    @Override
    public void startText(final ITextContent text) {
        if (isHidden(text.getStyle())) {
            logger.log(Level.FINE, "Skipping Hidden text");
            return;
        }

        logger.log(Level.FINE, "Start text");

        if (writeData) {
            if (text instanceof IDataContent) {
                writer.writeField(((IDataContent) text).getValue());
            } else {
                writer.writeField(text.getText());
            }
            currentColumn++;
        }
    }

    @Override
    public void endCell(final ICellContent cell) throws BirtException {
        if (isHidden(cell.getStyle())) {
            logger.log(Level.FINE, "Skipping Hidden cell");
            return;
        }

        if ((currentColumn < totalColumns) && writeData) {
            writer.writeSeperator();
        }
    }

    @Override
    public void endRow(final IRowContent row) {
        if (writeData) {
            writer.writeEndRow();
        }

        writeData = true;
    }

    private boolean isHidden(final IStyle style) {
        String format = style.getVisibleFormat();

        if (format != null && (format.indexOf(EngineIRConstants.FORMAT_TYPE_VIEWER) >= 0 || format.indexOf(BIRTConstants.BIRT_ALL_VALUE) >= 0)) {
            return true;
        } else {
            return false;
        }
    }

    private boolean isRowInFooter(final IRowContent row) {
        IElement parent = row.getParent();
        if (!(parent instanceof IBandContent)) {
            return false;
        }

        IBandContent band = (IBandContent) parent;
        if (band.getBandType() == IBandContent.BAND_FOOTER) {
            return true;
        }
        return false;
    }

    private boolean isRowInHeaderExceptFirstHeader(final IRowContent row) {
        if (isFirstPage) {
            return false;
        }

        IElement parent = row.getParent();
        if (!(parent instanceof IBandContent)) {
            return false;
        }

        IBandContent band = (IBandContent) parent;
        if (band.getBandType() == IBandContent.BAND_HEADER) {
            return true;
        }

        return false;
    }
}
