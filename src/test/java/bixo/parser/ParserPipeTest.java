package bixo.parser;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Iterator;

import org.apache.hadoop.mapred.JobConf;
import org.archive.io.ArchiveReader;
import org.archive.io.ArchiveReaderFactory;
import org.archive.io.ArchiveRecord;
import org.archive.io.ArchiveRecordHeader;
import org.junit.Test;

import bixo.IConstants;
import bixo.fetcher.beans.FetchStatusCode;
import bixo.parser.html.HtmlParser;
import bixo.parser.html.Outlink;
import bixo.tuple.FetchContentTuple;
import bixo.tuple.FetchResultTuple;
import bixo.tuple.ParseResultTuple;
import cascading.CascadingTestCase;
import cascading.flow.Flow;
import cascading.flow.FlowConnector;
import cascading.pipe.Pipe;
import cascading.scheme.SequenceFile;
import cascading.tap.Lfs;
import cascading.tuple.Fields;
import cascading.tuple.Tuple;
import cascading.tuple.TupleEntryCollector;

public class ParserPipeTest extends CascadingTestCase {
    
    @Test
    public void testParserPipe() throws Exception {

        Pipe pipe = new Pipe("parse_source");
        ParserPipe parserPipe = new ParserPipe(pipe, new DefaultParserFactory());
        Lfs in = new Lfs(new SequenceFile(new Fields(IConstants.URL).append(FetchResultTuple.FIELDS)), "build/test-data/ParserPipeTest/in", true);
        Lfs out = new Lfs(new SequenceFile(ParseResultTuple.FIELDS), "build/test-data/ParserPipeTest/out", true);

        TupleEntryCollector write = in.openForWrite(new JobConf());

        ArchiveReader archiveReader = ArchiveReaderFactory.get("src/test-data/someHtml.arc");
        Iterator<ArchiveRecord> iterator = archiveReader.iterator();
        int max = 300;
        int count = 0;
        int validRecords = 0;
        while (count++ < max && iterator.hasNext()) {
            ArchiveRecord archiveRecord = (ArchiveRecord) iterator.next();
            ArchiveRecordHeader header = archiveRecord.getHeader();
            String url = header.getUrl();
            
            String protocol = "";
            try {
                protocol = new URL(url).getProtocol();
            } catch (MalformedURLException e) {
                // Ignore and skip
            }
            
            if (protocol.equals("http")) {
                validRecords += 1;
                int contentOffset = header.getContentBegin();
                long totalLength = header.getLength();
                int contentLength = (int)totalLength - contentOffset;
                
                long startTime = System.currentTimeMillis();
                archiveRecord.skip(contentOffset);
                byte[] content = new byte[contentLength];
                archiveRecord.read(content);

                String mimetype = header.getMimetype();
                FetchContentTuple contentTuple = new FetchContentTuple(url, url, System.currentTimeMillis(), content, mimetype, 0);
                Tuple tuple = Tuple.size(3);
                tuple.set(0, url);
                tuple.set(1, FetchStatusCode.FETCHED.ordinal());
                tuple.set(2, contentTuple.toTuple());
                write.add(tuple);
            }
        }

        write.close();
        FlowConnector flowConnector = new FlowConnector();
        Flow flow = flowConnector.connect(in, out, parserPipe);
        flow.complete();
        validateLength(flow, validRecords);

    }

    @SuppressWarnings("serial")
    public static class DefaultParserFactory implements IParserFactory {

        private static HtmlParser _parser = new HtmlParser();
        
        @Override
        public IParser newParser() {
            return new IParser() {
                
                @Override
                public ParseResultTuple parse(FetchContentTuple contentTuple) {
                    IParse parse = _parser.getParse(contentTuple).get(contentTuple.getBaseUrl());
                    
                    Outlink[] outlinks = parse.getData().getOutlinks();
                    String[] stringLinks = new String[outlinks.length];
                    
                    int i = 0;
                    for (Outlink outlink : outlinks) {
                        stringLinks[i++] = outlink.getToUrl();
                    }
                    
                    return new ParseResultTuple(parse.getText(), stringLinks);
                }
                
            };
        }
        
    }
    
   
    @SuppressWarnings("serial")
    public static class FakeParserFactor implements IParserFactory {

        @Override
        public IParser newParser() {
            return new IParser() {

                @Override
                public ParseResultTuple parse(FetchContentTuple contentTuple) {
                    return new ParseResultTuple("someText", new String[0]);
                }

            };
        }

    }
}