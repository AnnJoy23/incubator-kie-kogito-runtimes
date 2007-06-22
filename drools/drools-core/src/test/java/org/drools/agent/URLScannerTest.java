package org.drools.agent;

import java.io.File;
import java.io.FileInputStream;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Properties;

import junit.framework.TestCase;

import org.drools.common.DroolsObjectInputStream;
import org.drools.rule.Package;

public class URLScannerTest extends TestCase {

    public void testFileURLCache() throws Exception {
        File dir = RuleBaseAssemblerTest.getTempDirectory();
        
        String url = "http://localhost:8080/foo/bar.bar/packages/IMINYRURL/LATEST";
        String fileName = URLEncoder.encode( url, "UTF-8" );
        
        File f = new File(dir, fileName);
        
        Package p = new Package("x");
        
        
        RuleBaseAssemblerTest.writePackage( p, f );
        
        DroolsObjectInputStream in = new DroolsObjectInputStream(new FileInputStream(f));
        Package p_ = (Package) in.readObject();
        assertEquals("x", p_.getName());


        
    }
    
//    public void testGetURL() throws Exception {
//        String url = "http://localhost:8080/foo/bar.bar/packages/IMINYRURL/LATEST";
//        URL u = new URL(url);
//        assertEquals(url, URLScanner.getURL( u ));
//        //URLConnection con = u.openConnection();
//        //con.connect();
//    }
    
    public void testGetFiles() throws Exception {
        
        URL u1 = new URL("http://localhost:8080/foo/bar.bar/packages/IMINYRURL/LATEST");
        URL u2 = new URL("http://localhost:8080/foo/bar.bar/packages/IMINYRURL/PROD");
        URLScanner scan = new URLScanner();
        
        File dir = RuleBaseAssemblerTest.getTempDirectory();        
        File[] result = scan.getFiles( new URL[] {u1, u2}, dir );
        
        assertEquals(2, result.length);
        assertEquals(dir.getPath(), result[0].getParent());
        
        File f1 = result[0];
        File f2 = result[1];
        assertEquals("http%3A%2F%2Flocalhost%3A8080%2Ffoo%2Fbar.bar%2Fpackages%2FIMINYRURL%2FLATEST", f1.getName());
        assertEquals("http%3A%2F%2Flocalhost%3A8080%2Ffoo%2Fbar.bar%2Fpackages%2FIMINYRURL%2FPROD", f2.getName());
        
    }
    
    public void testConfig() throws Exception {
        URLScanner scan = new URLScanner();
        
        File dir = RuleBaseAssemblerTest.getTempDirectory();
        
        Properties config = new Properties();
        config.setProperty( RuleAgent.LOCAL_URL_CACHE, dir.getPath() );
        config.setProperty( RuleAgent.URLS, "http://goo.ber http://wee.waa" );
        
        scan.configure( config );
        
        assertNotNull(scan.lastUpdated);
        assertEquals(2, scan.urls.length);
        assertEquals("http://goo.ber", scan.urls[0].toExternalForm());
        assertEquals("http://wee.waa", scan.urls[1].toExternalForm());
        assertNotNull(scan.localCacheFileScanner);
        
        assertEquals(2, scan.localCacheFileScanner.files.length);
        
        assertEquals("http%3A%2F%2Fgoo.ber", scan.localCacheFileScanner.files[0].getName());
        assertEquals("http%3A%2F%2Fwee.waa", scan.localCacheFileScanner.files[1].getName());
        
        
        
    }
    
    public void testLastUpdatedError() {
        LastUpdatedPing ping = new LastUpdatedPing();
        assertTrue(ping.isError());
        ping.responseMessage = "ABC";
        ping.lastUpdated = 42;
        assertTrue(ping.isError());
        ping.responseMessage = "200 OK";
        assertFalse(ping.isError());
    }
    
    
}
