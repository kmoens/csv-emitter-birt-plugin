package org.eclipse.birt.report.engine.emitter.csv;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Writer for CSV files.
 *
 * @author Kenny Moens <kenny.moens@cipalschaubroeck.be>
 * @since 1.01.2.00
 */
public class CSVWriter {
    private static Logger logger = Logger.getLogger(CSVWriter.class.getName());

    private final String quote;
    private final String escapeQuote;
    private final String sep;

    private final SimpleDateFormat dateFormat;
    private final DecimalFormat decimalFormat;

    public CSVWriter() {
        this(CSVTags.QUOTE, CSVTags.SEMICOLON);
    }

    public CSVWriter(final String quote, final String sep) {
        this.quote = quote;
        this.sep = sep;

        escapeQuote = quote + quote;

        dateFormat = new SimpleDateFormat("yyyy-MM-dd");

        DecimalFormatSymbols s = new DecimalFormatSymbols();
        s.setDecimalSeparator(',');

        decimalFormat = new DecimalFormat("#.##");
    }

    private Writer writer;

    public void open(final OutputStream outputStream, final String encoding) {
        assert (encoding != null);
        assert (outputStream != null);

        try {
            writer = new BufferedWriter(new OutputStreamWriter(outputStream, encoding));
        } catch (UnsupportedEncodingException e) {
            logger.log(Level.SEVERE,"the character encoding {0} unsupported !", encoding); //$NON-NLS-1$
        }
    }

    public void close() {
        flush();
        try {
            writer.close();
        } catch (IOException ex) {
            logger.log(Level.SEVERE, ex.getMessage());
        }
    }

    public void flush() {
        try {
            writer.flush();
        } catch (IOException ex) {
            logger.log(Level.SEVERE, ex.getMessage());
        }
    }

    public void writeField(final Object value) {
        if (value == null) {
        } else if (value instanceof Byte || value instanceof Integer || value instanceof Long || value instanceof BigDecimal) {
            write(value.toString());
        } else if (value instanceof Number) {
            write(decimalFormat.format(value));
        } else if (value instanceof Date) {
            write(dateFormat.format((Date) value));
        } else if (value instanceof Calendar) {
            write(dateFormat.format(((Calendar) value).getTime()));
        } else {
            write(quote + value.toString().replace(quote, escapeQuote) + quote);
        }
    }

    public void writeSeperator() {
        write(sep);
    }

    public void writeEndRow() {
        write(CSVTags.NEWLINE);
    }

    private void write(final String text) {
        try {
            writer.write(text);
        } catch (IOException e) {
            throw new RuntimeException("Unable to write CSV file contenxt: " + e.getMessage(), e);
        }
    }
}
