package org.astrogrid.samp.bridge;

import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import org.astrogrid.samp.SampUtils;
import org.astrogrid.samp.httpd.HttpServer;

/**
 * HttpServer handler for turning the URL of one icon into the URL of 
 * another, related icon.
 *
 * @author   Mark Taylor
 * @since    23 Jul 2009
 */
abstract class IconAdjuster implements HttpServer.Handler {

    private final URL baseUrl_;
    private static final String OUTPUT_FORMAT_NAME = "png";
    private static final String OUTPUT_MIME_TYPE = "image/png";

    /**
     * Constructor.
     *
     * @param   server  server with which this handler will be used
     * @param   basePath  path at which the dynamic URLs generated by
     *          this server will be rooted
     */
    public IconAdjuster( HttpServer server, String basePath ) {
        if ( ! basePath.startsWith( "/" ) ) {
            basePath = "/" + basePath;
        }
        if ( ! basePath.endsWith( "/" ) ) {
            basePath = basePath + "/";
        }
        try {
            baseUrl_ = new URL( server.getBaseUrl(), basePath );
        }
        catch ( MalformedURLException e ) {
            throw (AssertionError) new AssertionError().initCause( e );
        }
    }

    /**
     * Produces an adjusted image for serving.
     *
     * @param  inImage  input image
     * @return   adjusted version of <code>inImage</code>
     */
    public abstract RenderedImage adjustImage( BufferedImage inImage );

    /**
     * Returns a URL at which the dynamically adjusted version of the icon
     * at the given URL will be served.
     *
     * @param  iconUrl  URL of existing icon (GIF, PNG or JPEG)
     */
    public URL exportAdjustedIcon( URL iconUrl ) {
        try {
            return new URL( baseUrl_ + "?"
                          + SampUtils.uriEncode( iconUrl.toString() ) );
        }
        catch ( MalformedURLException e ) {
            throw (AssertionError) new AssertionError().initCause( e );
        }
    }

    /**
     * Returns the URL at which the underlying icon for the one represented
     * by the given server path.  The <code>resourcePath</code> should be 
     * the path part of a URL returned from an earlier call to 
     * {@link #exportAdjustedIcon}.
     *
     * @param   resourcePath  path part of a URL requesting an adjusted icon
     * @return   original icon corresponding to resourcePath, or null
     *           if it doesn't look like a path this object dispensed
     */
    private URL getOriginalUrl( String resourcePath )
            throws MalformedURLException {

        // If there's no query part, it's not one of ours.
        URL resourceUrl = new URL( baseUrl_, resourcePath );
        String query = resourceUrl.getQuery();
        if ( query == null ) {
            return null;
        }
        else {

            // If the base does not match our base URL, it's not one of ours.
            String base = resourceUrl.toString();
            base = base.substring( 0, base.length() - query.length() );
            if ( ! base.equals( baseUrl_.toString() + "?" ) ) {
                return null;
            }

            // Otherwise, try to interpret the query as a URL.
            // It should be the URL of the original icon.
            // If it's not, an exception will result.

            String qurl;
            try {
                qurl = SampUtils.uriDecode( query );
            }
            catch ( RuntimeException e ) {
                throw (MalformedURLException)
                      new MalformedURLException().initCause( e );
            }
            return new URL( qurl );
        }
    }

    public HttpServer.Response serveRequest( HttpServer.Request request ) {
        URL baseIconUrl;
        try { 
            baseIconUrl = getOriginalUrl( request.getUrl() );
        }
        catch ( MalformedURLException e ) {
            return HttpServer.createErrorResponse( 404, "Not found", e );
        }
        if ( baseIconUrl == null ) {
            return null;
        }

        // Prepare the headers.
        Map hdrMap = new HashMap();
        hdrMap.put( "Content-Type", OUTPUT_MIME_TYPE );

        // Generate the response object.
        String method = request.getMethod();
        if ( "HEAD".equals( method ) ) {
            return new HttpServer.Response( 200, "OK", hdrMap ) {
                public void writeBody( OutputStream out ) {
                }
            };
        }
        else if ( "GET".equals( method ) ) {
            BufferedImage baseImage;
            try {
                baseImage = ImageIO.read( baseIconUrl );
                if ( baseImage == null ) {
                    throw new FileNotFoundException( baseIconUrl.toString() );
                }
            }
            catch ( IOException e ) {
                return HttpServer
                      .createErrorResponse( 500, "Server I/O error", e );
            }
            final RenderedImage adjustedImage = adjustImage( baseImage );
            return new HttpServer.Response( 200, "OK", hdrMap ) {
                public void writeBody( OutputStream out )
                        throws IOException {
                    ImageIO.write( adjustedImage, OUTPUT_FORMAT_NAME, out );
                }
            };
        }
        else {
            return HttpServer
                  .create405Response( new String[] { "GET", "HEAD" } );
        }
    }
}
