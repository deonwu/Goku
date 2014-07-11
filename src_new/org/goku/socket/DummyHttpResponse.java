package org.goku.socket;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.Locale;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;

public class DummyHttpResponse implements HttpServletResponse {
	private PrintWriter writer = null;
	private SocketClient client = null;
	private ByteArrayOutputStream buffer = new ByteArrayOutputStream(1024 * 4){
		public void flush() throws IOException{
			if(this.size() > 0){
				client.write(this.toByteArray());
				this.reset();
			}
		}
	};
	
	public DummyHttpResponse(SocketClient client){
		this.client = client;
		try {
			writer = new PrintWriter(new OutputStreamWriter(buffer, this.client.encoding));
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
	}

	public void flushBuffer() throws IOException {
		writer.flush();
	}

	public int getBufferSize() {
		return 0;
	}

	public String getCharacterEncoding() {
		return null;
	}

	public String getContentType() {
		return null;
	}

	public Locale getLocale() {
		return null;
	}

	public ServletOutputStream getOutputStream() throws IOException {
		return null;
	}

	public PrintWriter getWriter() throws IOException {
		return this.writer;
	}

	public boolean isCommitted() {
		return false;
	}

	public void reset() {
	}

	public void resetBuffer() {

	}

	public void setBufferSize(int arg0) {

	}

	public void setCharacterEncoding(String encoding) {
		this.client.encoding = encoding;		
	}

	public void setContentLength(int arg0) {

	}

	public void setContentType(String arg0) {

	}

	public void setLocale(Locale arg0) {

	}

	public void addCookie(Cookie arg0) {

	}

	public void addDateHeader(String arg0, long arg1) {

	}

	public void addHeader(String arg0, String arg1) {

	}

	public void addIntHeader(String arg0, int arg1) {

	}

	public boolean containsHeader(String arg0) {
		return false;
	}

	public String encodeRedirectURL(String arg0) {
		return null;
	}

	/**
	 * @deprecated
	 */
	public String encodeRedirectUrl(String arg0) {
		return null;
	}

	public String encodeURL(String arg0) {
		return null;
	}

	/**
	 * @deprecated
	 */	
	public String encodeUrl(String arg0) {
		return null;
	}

	public void sendError(int arg0) throws IOException {

	}

	public void sendError(int arg0, String arg1) throws IOException {

	}

	public void sendRedirect(String arg0) throws IOException {

	}

	public void setDateHeader(String arg0, long arg1) {

	}

	public void setHeader(String arg0, String arg1) {

	}

	public void setIntHeader(String arg0, int arg1) {

	}

	public void setStatus(int arg0) {
	}

	/**
	 * @deprecated
	 */
	public void setStatus(int arg0, String arg1) {

	}

}
