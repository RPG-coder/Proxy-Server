/*************************************************************************

 BSD 3-Clause License

Copyright (c) 2021, Rahul Gautham Putcha
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

3. Neither the name of the copyright holder nor the names of its
   contributors may be used to endorse or promote products derived from
   this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

*************************************************************************/
/*
 *  ProxyServer_v1.23 | Copyright (c) 2021, Rahul Gautham Putcha
 *  Number of lines of code with comment : 350+ < 750
 */
//Import Files
import java.net.InetAddress;
//Additional Imports
import java.net.ServerSocket;
import java.net.Socket;
import java.net.InetSocketAddress;

public class ProxyServer {
    // NOC these 3 fields
    private byte []     HOST;      /* should be initialized to 1024 bytes in the constructor */
    private int         PORT;      /* port this ProxyServer should listen on, from cmdline        */
    private InetAddress PREFERRED; /* must set this in dns() */

    // Additional fields
    private byte[] FILE, VERSION, headers; 	// Buffer for client requested_resource, HTTP_version_support, header_line(s)
    private int protocolPort;      		// What port does the server listen on?
    private boolean isFTP;         		// If it is FTP or HTTP?
    private int MAX_REQ_SIZE;      		// MAX Supported Request Message size
    private byte[][] ignoreHeader; 		// Headers to be Ignored / Removed for Implementation Compliancy
    private byte[] ignoreHopHop;
    //----------------------------------------------------------------------
    /*
     * @function : main() of the ProxyServer class
     * @argument : taking one cmd line argument i.e., the port number at argument a[0].
     */
    public static void main(String [] a) { // Note: Takes Port number as Argument 
        ProxyServer proxy = new ProxyServer(Integer.parseInt(a[0]));
        proxy.run(2);
    }

    // @function: ProxyServer <<constructor>> (called default!)
    ProxyServer(int port) {
        PORT = port;
        // Additional Code
        this.PREFERRED = null;
        this.HOST = new byte[1024];
        this.VERSION = null; this.FILE = null; this.isFTP = false; this.headers = null;
        this.protocolPort = 80;
        this.MAX_REQ_SIZE = 65535;
        this.ignoreHeader = new byte[][]{
                new byte[]{'k', 'e', 'e', 'p', '-', 'a', 'l', 'i', 'v', 'e'},
                new byte[]{'p', 'r', 'o', 'x', 'y'},
                new byte[]{'t', 'e'},
                //new byte[]{'t', 'r', 'a', 'n', 's', 'f', 'e', 'r', '-', 'e', 'n', 'c', 'o', 'd', 'i', 'n', 'g'},
                new byte[]{'t', 'r', 'a', 'i', 'l', 'e', 'r'},
                new byte[]{'u', 'p', 'g', 'r', 'a', 'd', 'e'}
        };
        this.ignoreHopHop = null;
    }

    /*
     * @function : parse()
     * @details  : Parse only request line in buffer & sets the variable
     *               this.HOST    = URL part in the buffer(request)
     *               this.FILE    = URL/FILE part in the buffer(request)
     *               this.VERSION = VERSION part in the buffer(request)
     *             Check for request line errors
     * HTTP message format: ^(GET)(single-space)({PROTOCOL:\\}+HOST+{:PORT}+FILE)(single-space)(VERSION)(\r\n)$
     */
    int parse(byte [] buffer) throws Exception {
        int startURLIndex, endURLIndex, i = 0;

        // Check if the request line start with "GET "
        i = this.matchBytes(buffer, (new byte[]{'G', 'E', 'T', ' '}), false, i, true);
        if (i<4) { // If the line doesn't start with a "GET " METHOD
            System.out.println("ERROR: Request provided METHOD not supported / RES: 501 Not Implemented");
            return 2; // means "501 Not Implemented"
        }if(i >= buffer.length) { // If we reached the end of request line, then its an ERROR!!
            System.out.println("ERROR: END OF LINE reached <<flag: after METHOD>> / RES: 400 Bad Request");
            return 1; // means "400 Bad request"
        }

        // We check what protocol method we are using "http://" for http_request() or "ftp://" for ftp_request() i.e., if any??
        this.protocolPort = 80; this.isFTP = false; // by default http server-side port is 80.
        startURLIndex = this.matchBytes(buffer,(new byte[]{'h','t','t','p',':','/','/'}),false,i, true); // Check if "http://" is being asked
        if(startURLIndex == i){ // if not "http://" (the index remains same), then check for "ftp://"
            startURLIndex = this.matchBytes(buffer, (new byte[]{'f', 't', 'p', ':', '/', '/'}),false, i, true); // Check if "ftp://" is being asked
            if(startURLIndex == i){ // If it is not any, (ProxyServer: Supports url only if the protocol used (http/ftp) is present...
                return 1; // return 400 BAD Request
            }
            this.protocolPort = 21; this.isFTP = true; // by default ftp server-side port to port 21.
        }endURLIndex = startURLIndex; // Knowing that the domain name starts from startURLIndex we extract till we encounter a space(' '), port(':'), filepath('/')
        while( (endURLIndex < buffer.length) && buffer[endURLIndex] != '/' && buffer[endURLIndex] != ' ' && buffer[endURLIndex] != ':' ) {
            if(buffer[endURLIndex] == '\r' || buffer[endURLIndex] == '\n'){ // if it is end of line condition
                return 1; // then Invalid syntax (End of request line) (Version not specified) means "400 Bad request"
            }
            endURLIndex++;
        }// Program Counter here means no error in reading domain
        if((startURLIndex<endURLIndex) && (endURLIndex-startURLIndex)<1024)
            this.HOST = extractBytes(buffer, startURLIndex, endURLIndex); // Whatever comes in between startURLIndex and endURLIndex is the HOST name
        else return 1; // else it means no domain name present in request line "400 Bad request"

        if(endURLIndex >= buffer.length) return 1; // End of request line after reading domain name, "400 Bad request"

        //Getting an Optional port, in case a ':' is present in the request message take what ever comes till we encounter a space or / else return error
        int digit;
        if(buffer[endURLIndex]==':') {
            this.protocolPort = 0;
            endURLIndex++; // whatever comes after ':' is the port
            while (endURLIndex<buffer.length && (buffer[endURLIndex]!=' ') && (buffer[endURLIndex]!='/')) {
                if (buffer[endURLIndex] == '\r' || buffer[endURLIndex] == '\n') return 1; // End of request line
                digit = byteToAsciiInt(buffer[endURLIndex]);
                if(digit!=-1) this.protocolPort=(this.protocolPort*10)+digit;
                else return 1; // port value is a character other than digits
                if(this.protocolPort>65535) return 1; // port value is greater than 16-bit
                endURLIndex++;
            }
        }if(endURLIndex >= buffer.length) return 1; // End of request line after reading domain name, "400 Bad request"

        startURLIndex = endURLIndex;
        if(buffer[endURLIndex]=='/') { // Getting an Optional File, in case a '/' is present in the request message take whatever comes till we encounter a space,
            while (endURLIndex<buffer.length && (buffer[endURLIndex]!=' ')) {
                if (buffer[endURLIndex] == '\r' || buffer[endURLIndex] == '\n') return 1; // End of request line
                endURLIndex++;
            }this.FILE = extractBytes(buffer, startURLIndex, endURLIndex);
        }endURLIndex++; //Counting a Delimiter Space
        if(endURLIndex >= buffer.length) return 1; // End of request line after reading domain name, "400 Bad request"

        // Process of Checking HTTP version
        // NOTE: This is a HTTP/1.1 PROXY, hence this support all lower version and throws error on higher version. ref - RFC:2145
        startURLIndex = endURLIndex;
        endURLIndex = this.matchBytes(buffer,new byte[]{'H', 'T', 'T', 'P', '/', '1', '.', '1'}, false,startURLIndex, true);
        if(endURLIndex==startURLIndex) {// if version is not HTTP/1.1
            endURLIndex = this.matchBytes(buffer, new byte[]{'H', 'T', 'T', 'P', '/', '1', '.', '0'}, false, startURLIndex, true);
            if (endURLIndex == startURLIndex) {//if version is not HTTP/1.0
                endURLIndex = this.matchBytes(buffer, new byte[]{'H', 'T', 'T', 'P', '/', '0', '.', '9'}, false, startURLIndex, true);
                if (endURLIndex == startURLIndex) {// ERROR if version is not HTTP/0.9
                    return 3; // this means "505 HTTP Version Not Supported" 2.0 is an ERROR
                }else { this.VERSION = new byte[]{'H', 'T', 'T', 'P', '/', '0', '.', '9'}; }
            }else { this.VERSION = new byte[]{'H', 'T', 'T', 'P', '/', '1', '.', '0'}; }
        }else{ this.VERSION = new byte[]{'H', 'T', 'T', 'P', '/', '1', '.', '1'}; }
        startURLIndex = endURLIndex;
        endURLIndex = this.matchBytes(buffer, new byte[]{'\r','\n'}, false, startURLIndex, true); // End of Line found
        if(startURLIndex==endURLIndex) return 1; // end of line not found, "400 Bad request"
        return 0; // ALL IS WELL!!
    }

    // Note: dns() must set PREFERRED - TA stub note
    int dns(int X){ // X = 0 to 10; # It indicate indicating what should be done for now use 0 or 2 for both?
        try{
            // Fetch all IPaddresses of a given "HOST" by name from the Root server
            InetAddress[] addressList = InetAddress.getAllByName(bytesToString(this.HOST,0,this.HOST.length));
            //Remove all IPv6 address and get work only with IPv4 addresses
            addressList = this.removeIPv6(addressList);
            // This does all our IPaddress selection from list of IPaddresses addressList provided by the Root server
            if(addressList.length>1) this.PREFERRED = this.selectIPaddress(addressList);
            else this.PREFERRED = addressList[0]; // Get the default IPaddress provided by the Root server.
        }catch(Exception e){
            //e.printStackTrace();
            return 1; //ERROR in fetching the HOST IPaddress
        }
        return 0; // If success on retrieving the dns return 0, else return 1 for ERROR
    }

    int ftp_fetch(Socket c){
        // Initialization
        Socket p, passive=null; 					// Client Socket
        byte[] buffer, responseCode, responseMessage = new byte[0];	// Message Buffers
        byte[] data;							// Data Transfer Buffer
        // Variables for Processing
        int i=0;
        // Variables for PASSIVE MODE processing
        int port=0, octet=0;
        byte[] ipAddressByte = {0, 0, 0, 0};
        int[] portBytes = {0, 0};
        int ipIndex = 0, portIndex = 0;
        // Variables for Data Mode Processings
        int nBytes = 0, size, cmdStep=0;
        byte[] filename = new byte[0], filesize = new byte[0];
        // Variables for Error Condition Processings
        boolean error = false; byte[] errorMessage= new byte[0];
	long mustEndTime,currentTime;

        try {// Processing with Exception Handling
            p = new Socket(); // Connect to Server Socket
            try {
                p.connect(new InetSocketAddress(this.PREFERRED, this.protocolPort), 5000);
            }catch(Exception e){
                System.out.println("ERROR: Timeout Occured! / RES: 524 A Timeout Occurred");
                errorMessage = new byte[]{'5', '2', '4', ' ', 'A', ' ', 'T', 'i', 'm', 'e', 'o', 'u', 't', ' ', 'O', 'c', 'c', 'u', 'r', 'r', 'e', 'd', '\r', '\n', '\r', '\n'};
                c.getOutputStream().write(errorMessage,0,errorMessage.length);
                return nBytes;
            }
            if(this.FILE == null){ this.FILE = new byte[]{'/','i','n','d','e','x','.','h','t','m','l'}; } //Default file name
            else{
	      /*FIXME: check for %(hex code) notations...*/ 
	      int d = 0;
	      byte[] NEWFILE = new byte[FILE.length];
	      for(int f=0;f<FILE.length;f++){
	        if(FILE[f]=='%' && (f+1)<FILE.length && (f+2)<FILE.length){
		  NEWFILE[d++] = hexToAscii(new byte[]{FILE[f+1],FILE[f+2]});
		  f=f+2;
		}
	        else
		  NEWFILE[d++] = FILE[f];
	      }
	      FILE = extractBytes(NEWFILE,0,d);
	    } //Can be done by introducing \<space> in place of %20
            buffer = new byte[2048];
	    mustEndTime = System.currentTimeMillis()+20000;
            p.setSoTimeout(20000);
            while ((p.getInputStream().read(buffer, 0, 2048)) != -1) {
                error = false;
                responseCode = extractBytes(buffer, 0, 3);
                // Remove: System.out.println(bytesToString(buffer, 0, buffer.length));
                switch (bytesToString(responseCode, 0, responseCode.length)) {
                    case "220": // Remove: System.out.println("CONNECTION_OK: Service Ready for new user.");
                        responseMessage = new byte[]{'U', 'S', 'E', 'R', ' ', 'a', 'n', 'o', 'n', 'y', 'm', 'o', 'u', 's', '\r', '\n'};
			cmdStep = 1;
                        break;

                    case "331": // Remove: System.out.println("User name OK, need password.");
                        responseMessage = new byte[]{'P', 'A', 'S', 'S', ' ', 'a', 'n', 'o', 'n', 'y', 'm', 'o', 'u', 's', '\r', '\n'};
			cmdStep = 2;
                        break;

                    case "230": // Remove: System.out.println("PASS Ok!");
                        responseMessage = new byte[]{'P', 'A', 'S', 'V', '\r', '\n'}; // Getting Ready on Passive Mode
			cmdStep = 3;
                        break;

                    case "227": // Remove: System.out.println("Entering to PASSIVE MODE!!"); System.out.println(bytesToString(buffer,0,buffer.length))
                        responseMessage = concatBytes(concatBytes(new byte[]{'R', 'E', 'T', 'R', ' '}, this.FILE ), new byte[]{'\r', '\n'});
			cmdStep = 4;
                        for (i = 0; i < buffer.length && buffer[i] != '('; i++); i++;
                        for (; i < buffer.length && buffer[i] != ')'; i++) {
                            octet = 0;
                            // Convert string h1,h2,h3,h4,p1,p2 to IP address<<byte[]>> and port<<int>>
                            while (i < buffer.length && buffer[i] != ',' && buffer[i] != ')') {
                                octet = octet * 10 + ((buffer[i] & 0xFF) - 48); i++;
                            }
                            if (ipIndex < 4) {
                                ipAddressByte[ipIndex] = (byte) (octet&0xFF); // Remove: System.out.println(ipAddressByte[ipIndex]);
                                ipIndex++;
                            } else if (portIndex < 2) {
                                portBytes[portIndex] = octet;
                                portIndex++;
                            }
                        }
                        port = (portBytes[0] << 8) | (portBytes[1]);
                        passive = new Socket();
                        passive.connect(new InetSocketAddress(InetAddress.getByAddress(ipAddressByte),port), (int)(mustEndTime-System.currentTimeMillis()));
                        if(!passive.isConnected()){
                            error=true;
                            System.out.println("ERROR: Passive Mode not Set up. Connection Timed!!. / RES: 504 Gateway Timeout");
                            errorMessage = new byte[]{'5', '0', '4', ' ', 'G', 'a', 't', 'e', 'w', 'a', 'y', ' ', 'T', 'i', 'm', 'e', 'o', 'u', 't'};
                        }
                        break;
                    case "150": // Remove: System.out.println("FILE EXIST >> Switching to Data Connection MODE Ok!!"
		    	cmdStep = 5;
                        // Extracting only filename for FILE path to get the name of the download file
                        for(i=(FILE.length-1);i>=0;i--) if(FILE[i]=='/') break;
			if((i+1)<FILE.length)
                          filename = extractBytes(FILE,i+1,FILE.length);
                        else{ filename = this.FILE = new byte[]{'/','i','n','d','e','x','.','h','t','m','l'}; } // Setting Default filename
                        if(isUnstructured(filename)){
                           responseMessage = new byte[]{'T', 'Y', 'P', 'E', ' ', 'I', '\r', '\n'};
                           p.getOutputStream().write(responseMessage,0,responseMessage.length);
			   try{
			     p.setSoTimeout(4000);
                             buffer = new byte[2048];
                             if(p.getInputStream().read(buffer,0,buffer.length)!=0);
			   }catch(Exception e){
			     //Avoid wait
			   }
                        }
                        // Getting File size
                        responseMessage = concatBytes(concatBytes(new byte[]{'S','I','Z','E',' '}, this.FILE), new byte[]{'\r', '\n'});
                        p.getOutputStream().write(responseMessage,0,responseMessage.length);
			try{
                          buffer = new byte[2048];
			  p.setSoTimeout(4000);
                          if(p.getInputStream().read(buffer,0,buffer.length)>0 && matchBytes(buffer,new byte[]{'2','1','3',' '},false,0,true)!=0){
                              filesize = extractBytes(buffer,4,buffer.length-1);
                          }else{System.out.println("WARNING: SIZE cmd not supported by server, FILE is sent w/o Content-Length:<<SIZE>> header line");}
			}catch(Exception e){
			  //Avoid wait
			}
                        try {
                            if (passive != null && filename.length>0) {
                                byte[] requestLine = concatBytes(this.VERSION, new byte[]{' ', '2', '0', '0', ' ', 'O', 'K', '\r', '\n'});
                                byte[] contentLength = new byte[0];

                                // Setting the file-size parameters via, Content-Length (used in download file size)
                                if(filesize.length>0)
                                    contentLength = concatBytes(new byte[]{'\r', '\n','C', 'o', 'n', 't', 'e', 'n', 't', '-', 'L', 'e', 'n', 'g', 't', 'h',':',' '},
                                            concatBytes(filesize,new byte[]{'\r', '\n', '\r', '\n'}));

                                byte[] headerLine = concatBytes(new byte[]{'C', 'o', 'n', 't', 'e', 'n', 't', '-', 'D', 'i', 's', 'p', 'o', 's', 'i', 't', 'i', 'o', 'n', ':',
                                                ' ', 'a', 't', 't', 'a', 'c', 'h', 'm', 'e', 'n', 't', ';', ' ', 'f', 'i', 'l', 'e', 'n', 'a', 'm', 'e', '=', '"'},
                                        concatBytes(concatBytes(filename,new byte[]{'"'}), (contentLength.length>0)?contentLength:new byte[]{'\r', '\n', '\r', '\n'})
                                );
                                // Start sending response message to the client
                                c.getOutputStream().write( concatBytes(requestLine, headerLine),0, (requestLine.length+headerLine.length));
                                data = new byte[2048];
                                while ((size = passive.getInputStream().read(data, 0, 2048)) != -1) { // Getting the file data, via Data-Connection
                                    c.getOutputStream().write(data,0,size);
                                    data = new byte[2048];
                                    nBytes += size;
                                }
                            }
                        } catch (Exception e) {
                            //e.printStackTrace();
                        } finally {
                            try {
                                p.setSoTimeout(7000);   // Wait for
                                p.getInputStream().read(buffer, 0, 2048);
                                responseCode = extractBytes(buffer, 0, 3);
                                if (matchBytes(responseCode, new byte[]{'2', '2', '6'}, false, 0, true) != 0 || matchBytes(responseCode, new byte[]{'2', '5', '0'}, false, 0, true) != 0) {
                                    p.getOutputStream().write(new byte[]{'Q', 'U', 'I', 'T', '\r', '\n'},0,6);
                                }
				return nBytes;
                            }catch (Exception e){
                                p.getOutputStream().write(new byte[]{'Q', 'U', 'I', 'T', '\r', '\n'},0,6);
                                p.close();
                                return nBytes;
                            }
                        }

                    case "120": error=true;
                        System.out.println("ERROR: Server is taking too much time to accept a new USER. / RES: 504 Gateway Timeout");
                        errorMessage = new byte[]{'5', '0', '4', ' ', 'G', 'a', 't', 'e', 'w', 'a', 'y', ' ', 'T', 'i', 'm', 'e', 'o', 'u', 't'};
                        break;
                    case "430":
                    case "530": error=true;
                        System.out.println("ERROR: Proxy is not anonymously authorized to access the server. / RES: 401 Unauthorized");
                        errorMessage = new byte[]{'4', '0', '1', ' ', 'U', 'n', 'a', 'u', 't', 'h', 'o', 'r', 'i', 'z', 'e', 'd'};
                        break;
                    case "421": error=true;
                        System.out.println("ERROR: Server is temporarily or permanently unavailable. <<Server-side shutdown>>");
                        errorMessage = new byte[]{'5', '0', '0', ' ', 'I', 'n', 't', 'e', 'r', 'n', 'a', 'l', ' ', 'S', 'e', 'r', 'v', 'e', 'r', ' ', 'E', 'r', 'r', 'o', 'r'};
                        break;
                    case "425": error=true;
                        System.out.println("ERROR: Server is temporarily or permanently unavailable.");
                        errorMessage = new byte[]{'5', '0', '3', ' ', 'S', 'e', 'r', 'v', 'i', 'c', 'e', ' ', 'U', 'n', 'a', 'v', 'a', 'i', 'l', 'a', 'b', 'l', 'e'};
                        break;
                    case "500":
                    case "550":
                    case "450": error=true;
                        System.out.println("ERROR: File not Found!!.");
                        errorMessage = new byte[]{'4', '0', '4', ' ', 'F', 'i', 'l', 'e', ' ', 'N', 'o', 't', ' ', 'F', 'o', 'u', 'n', 'd'};break;
                    default:
		        error = true;
		    	System.out.println("REASON: "+(
			(cmdStep==1||cmdStep==2)?"Authentication Required":
			(cmdStep==3)?"PASV mode not Supported!":
			"Failed to Retrieve the file - File may not exist"));
                        System.out.println("ERROR: Server threw ERR{"+bytesToString(responseCode, 0, responseCode.length)+"} / RES: 400 Bad Request");
                        errorMessage = new byte[]{'4', '0', '0', ' ', 'B', 'a', 'd', ' ', 'R', 'e', 'q', 'u', 'e', 's', 't'};
                }
                if(error) {
                    errorMessage = concatBytes(concatBytes(this.VERSION, concatBytes(new byte[]{' '},errorMessage)),new byte[]{'\r','\n','\r','\n'});
                    c.getOutputStream().write(errorMessage,0,errorMessage.length);
                    p.close();
                    return nBytes;
                }
                // Remove: System.out.println(bytesToString(responseMessage,0,responseMessage.length));
                if(responseMessage.length>0)
                    p.getOutputStream().write(responseMessage, 0, responseMessage.length);
                buffer = new byte[2048];

		currentTime = System.currentTimeMillis();
		if(mustEndTime>currentTime){
		  //System.out.println("Time:"+((int)(mustEndTime-currentTime)/1000));
                  p.setSoTimeout((int)(mustEndTime-currentTime));
		}
		else{
		  throw new Exception();
		}
            }
	}
	catch(Exception e){
            //e.printStackTrace();
            System.out.println("ERROR: Server has disconnected its socket on Control connection / RES: 503 Service Unavailable");
	    try{
               errorMessage = concatBytes(concatBytes(this.VERSION, concatBytes(new byte[]{' '}, new byte[]{'5', '0', '3', ' ', 'S', 'e', 'r', 'v', 'i', 'c', 'e', ' ', 'U', 'n', 'a', 'v', 'a', 'i', 'l', 'a', 'b', 'l', 'e'})), new byte[]{'\r','\n', '\r','\n'});
               c.getOutputStream().write(errorMessage,0,errorMessage.length);
	    }catch(Exception ex){
		// client side problem
	    }
        }
        return nBytes;
    }

    int http_fetch(Socket c) {
        Socket p;
        byte[] response = new byte[2048];
        int nBytes = 0;
        try {
            p = new Socket(); // peer, connection to HOST OutputStream
            try {
                p.connect(new InetSocketAddress(this.PREFERRED, this.protocolPort), 5000);
            }catch(Exception e){
                System.out.println("ERROR: Timeout Occured! / RES: 524 A Timeout Occurred");
                response = new byte[]{'5', '2', '4', ' ', 'A', ' ', 'T', 'i', 'm', 'e', 'o', 'u', 't', ' ', 'O', 'c', 'c', 'u', 'r', 'r', 'e', 'd', '\r', '\n', '\r', '\n'};
                c.getOutputStream().write(response,0,response.length);
                return nBytes;
            }
            byte[] request;
            //Create Request Message to be sent to server, EOM(END OF MESSAGE IS INCLUSIVE IN THE headers)
            if(FILE==null) request = concatBytes(concatBytes(concatBytes(new byte[]{'G', 'E', 'T', ' ', '/', ' '},this.VERSION),new byte[]{'\r', '\n'}),headers);
            else {
                // Why HTTP/1.1? Why not take what supplied by client?
                // Because, RFC2616, RFC2145 mentions not to send client supported version in request message
                // This can us to give error message from server-side, even if the requested-resource is of proper form.
                // HTTP/1.1 is backward compatible.
                request = concatBytes(
                        concatBytes(concatBytes(concatBytes(new byte[]{'G', 'E', 'T', ' '}, FILE), new byte[]{' ','H', 'T', 'T', 'P', '/', '1', '.', '1'}),
                                new byte[]{'\r', '\n'}), headers
                );
                System.out.println(bytesToString(FILE,0,this.FILE.length));
            }
            p.getOutputStream().write(request,0, request.length); // Send Rewritten Request MSG
            int k =0;
            try{
                p.setSoTimeout(20000);
                while ((k=p.getInputStream().read(response,0,2048))>0) { // Get Response MSG
                    nBytes += k;
                    c.getOutputStream().write(response,0, k); // Forward Response Message
                    response = new byte[2048];
                    p.setSoTimeout(20000);
                }
            }catch(Exception e){
                System.out.println("ERROR: Timeout Occured! / RES: 524 A Timeout Occurred");
                response = new byte[]{'5', '2', '4', ' ', 'A', ' ', 'T', 'i', 'm', 'e', 'o', 'u', 't', ' ', 'O', 'c', 'c', 'u', 'r', 'r', 'e', 'd', '\r', '\n', '\r', '\n'};
                c.getOutputStream().write(response,0,response.length);
            }
            try{
                p.close(); // Closing Socket connection
            }catch(Exception e){
                //e.printStackTrace();
            }
        } catch (Exception e) {
            //e.printStackTrace();
            return nBytes;
        }
        return nBytes;
    }

    /*
     * @function    : run
     * @description : Running ProxyServer by parse(), dns(), http_request(), ftp_request()
     */
    int run(int X) {
        ServerSocket s0 = null;
        Socket       s1 = null;
        byte []      b0;        // ADC - general purpose buffer

        // Additional variables
        int parse_result, dns_result;			// Error condition storage
        int requestNo = 0,nBytes; 			// Incoming REQ Serial number, Number of Bytes
        int requestLineNo, requestSize, prevIndex;	// Variable specific to client or individual REQ MSG
        byte[] line, response;				// Line Buffer and Response MSG Buffer
        try{
            // Additional code :
            // Setup a server-side socket and bind Host IPaddress and a PORT to listen incoming request
            s0 = new ServerSocket();
            s0.bind(new InetSocketAddress(InetAddress.getLocalHost(),this.PORT)); // NOTE: On Client-Side run, change this to getByName("localhost"). On AFS, getLocalHost()
            System.out.println("ProxyServer Listening on socket "+s0.getLocalPort());
            while ( true ) {
                //Initializing All Variables for new Clients : Setting DEFAULTS
                this.HOST = null; this.FILE = null; this.VERSION = null; this.headers=null;
                parse_result = 0; dns_result = 0; nBytes = -1;
                b0   = new byte[MAX_REQ_SIZE]; 			// NEW BUFFER of MAX REQ SIZE
                requestLineNo=0; requestSize = 0; prevIndex = 0;
                this.ignoreHopHop = null;

                // accept client's request / listen to clients using socket declared as s1
                s1 = s0.accept(); //Accepting new Clients and their REQ MSG
                System.out.println(
                        "("+(++requestNo)+") "+"Incoming client connection from "+
                                (s1.getInetAddress().getHostAddress())+":"+s1.getPort()+
                                //" to me "+s0.getInetAddress().getHostAddress()+":"+s0.getLocalPort()  // If "to me" indicate to listening
                                //IPaddress or to current machine IPaddress
                                " to me "+s1.getLocalAddress().getHostAddress()+":"+s0.getLocalPort() // Prints Current system running the ProxyServer.java
                );
                // Parse request line and header configuration in request message : READING NEW REQ MSG
                while(true){ // Note: We can get invalid junk request messages which are handled gracefully in this section
                    //Reading byte by byte of the wire till we encounter a end of line
                    for(;(requestSize<MAX_REQ_SIZE-1) && (b0[requestSize]=(byte)(s1.getInputStream().read()&0xFF))!=-1;requestSize++){
                        // If Encounter a end of line
                        if(requestSize>=1 && ((char)b0[requestSize]=='\n') && ((char)b0[requestSize-1]=='\r')) {requestSize++; break;}
                        if(requestSize>=MAX_REQ_SIZE){// if request message size exceeds the MAX_REQ_SIZE
                            parse_result = 4;
                            System.out.print("ERROR: 413 Payload Too Large, ");
                            break;
                        }
                    }if(parse_result!=0) break;

                    // Parsing line by line
                    line = extractBytes(b0,prevIndex,requestSize);
                    if(requestLineNo==0){ // Parsing the  REQUEST_LINE
                        parse_result = parse(line);
                        if(parse_result!=0) break;
                    }else{ // Parsing Header Lines and endOfMessage
                        if(matchBytes(b0,new byte[]{'\r', '\n', '\r', '\n'},false, requestSize-4, true)==requestSize){ // If end of message break;
                            byte[] extraHeader = new byte[]{
                                    'C', 'o', 'n', 'n', 'e', 'c', 't', 'i', 'o', 'n', ':', ' ', 'c', 'l', 'o', 's', 'e', '\r', '\n',
                                    'E', 'x', 'p', 'e', 'c', 't', ':', ' ', '1', '0', '0', '-', 'c', 'o', 'n', 't', 'i', 'n', 'u', 'e', '\r', '\n',
                                    'V', 'i', 'a', ':', ' ', '1', '.', '1', ' ', 'a', 'f', 's', 'a', 'c', 'c', 'e', 's', 's', '3', '.', 'n', 'j', 'i', 't', '.', 'e', 'd', 'u', '\r', '\n'
                            };
                            headers = (headers != null)? concatBytes(headers,extraHeader):extraHeader;
                            headers = concatBytes(headers, line); //\r\n (END OF MESSAGE)
                            break;
                        }
                        // Remove non-compliant Header Tags
                        line = parseHeader(line);
                        if(line!=null) {
                            // Remove: System.out.print(bytesToString(line,0, line.length));
                            headers = headers != null ? concatBytes(headers, line) : line;
                        }
                    }
                    prevIndex = requestSize;
                    requestLineNo++;
                }

                if(parse_result==0) {
                    dns_result = dns(X); // On Successful parse
                    if(dns_result==0){
                        nBytes = (isFTP)?ftp_fetch(s1):http_fetch(s1);
                        System.out.println("REQ:" + ((this.HOST != null) ? bytesToString(this.HOST, 0, this.HOST.length) : "") +
                                ((this.FILE != null) ? bytesToString(this.FILE, 0, this.FILE.length) : "") + " ("
                                + nBytes + " bytes transferred)"
                        );
                    }else{
                        response = new byte[]{'H', 'T', 'T', 'P', '/', '1', '.', '1', ' ', '5', '0', '2', ' ', 'B', 'a', 'd', ' ', 'G', 'a', 't', 'e', 'w', 'a', 'y', '\r', '\n', '\r', '\n'};
                        s1.getOutputStream().write(response,0,response.length); //HTTP/1.1 502 Bad Gateway?
                        System.out.println("ERROR: DNS Failed to find the IP Address / RES: HTTP/1.1 502 Bad Gateway");
                    }
                }
                else{ // Handling Parse Errors
                    dns_result = 1; // By default dns will fail, since parse_result is giving fail result
                    switch(parse_result){
                        case 1:
                        default: response = new byte[]{'4', '0', '0', ' ', 'B', 'a', 'd', ' ', 'r', 'e', 'q', 'u', 'e', 's', 't', '\r', '\n', '\r', '\n'};break;
                        case 2: response = new byte[]{'5', '0', '1', ' ', 'N', 'o', 't', ' ', 'I', 'm', 'p', 'l', 'e', 'm', 'e', 'n', 't', 'e', 'd', '\r', '\n', '\r', '\n'};break;
                        case 3: response = new byte[]{'5', '0', '5', ' ', 'H', 'T', 'T', 'P', ' ', 'V', 'e', 'r', 's', 'i', 'o', 'n', ' ', 'N', 'o', 't', ' ',
                                'S', 'u', 'p', 'p', 'o', 'r', 't', 'e', 'd', '\r', '\n', '\r', '\n'};break;
                        case 4: response = new byte[]{'4', '1', '3', ' ', 'P', 'a', 'y', 'l', 'o', 'a', 'd', ' ', 'T', 'o', 'o', ' ', 'L', 'a', 'r', 'g', 'e', '\r', '\n', '\r', '\n'};break;
                    }
                    s1.getOutputStream().write(response, 0, response.length);
                    System.out.println("ERROR: in Parsing the Request Message / RES: "+bytesToString(response,0,response.length-4));
                }
                s1.close();
            }
        }catch(Exception e){
            e.printStackTrace();
            return 1;
        }finally {
            try{
                s0.close();
                return 0;
            }catch (Exception e){
                //e.printStackTrace();
            }
        }
    }

    //----------------------------------------------------------------------
    // Additional Functions (from ProxyServer Part-1 till ProxyServer Part-2)
    /*
     * @Function    : bytesToString
     * @description : converts byteArray b[startIndex:endIndex] to String outputString
     * @Why we need to use this in this Project: refer to function InetAddress[] InetAddress.getAllByName(String); , in int dns(int X)
     */
    String bytesToString(byte[] b, int start, int end){
        String outputString="";
        for(int i=start;i<end;i++) outputString += ((char) (b[i] & 0xFF));
        return outputString;
    }

    /*
     * @Function    : extractBytes
     * @description : Get subset of a byte array b from startIndex to endIndex(not inclusive).
     */
    byte[] extractBytes(byte[] b, int start, int end){ //the start index is included and the end index is excluded
        byte[] byteSubset = new byte[(end-start)];
        for(int i=start;i<end;i++) byteSubset[i - start] = b[i];
        return byteSubset;
    }

    /*
     * @Function    : selectIPaddress
     * @argument    : list of InetAddress ( IPaddress )
     * @description : selects the IPaddress that takes minimum time to respond (using Ping time / RTT time)
     */
    InetAddress selectIPaddress(InetAddress[] addressList) throws Exception{
        InetAddress preferredAddress = addressList[0];
        long startTime, endTime, smallestDelay;
        //Set smallestDelay is taken by first address in the address list
        startTime = System.currentTimeMillis();
        addressList[0].isReachable(2500); // lets keep our default timeout of response as 2.5 seconds = (2.5*10^3) ms = 2500 milliseconds : for the purpose of Efficiency of DNS Lookup
        endTime = System.currentTimeMillis();
        smallestDelay = (endTime-startTime);

        //Check the smallest delay taken within all addresses in the addressList
        for(int i=1;i<addressList.length;i++){
            // System.out.println("This is URL "+(i+1)+" : ",);
            //Timing of Ping request
            startTime = System.currentTimeMillis();
            addressList[i].isReachable(2500); // lets keep our default timeout of response as 2.5 seconds = (2.5*10^3) ms = 2500 milliseconds
            endTime = System.currentTimeMillis();
            // if delay between sending request and receiving response is less than smallestDelay
            if(smallestDelay > (endTime-startTime)){
                smallestDelay = (endTime-startTime);
                preferredAddress = addressList[i];
            }
        }
        //Return the most preffered IPaddress
        return preferredAddress;
    }

    /*
     * @function : removeIPv6
     * @argument : list of IPaddresses
     * @details  : for a given IPaddresses, the returned list will only contain IPv4 addresses
     */
    InetAddress[] removeIPv6(InetAddress[] addressList){
        int numberOfIPv4 = 0, i = 0;
        for(i=0;i<addressList.length;i++){
            if(isIPv4(addressList[i])) numberOfIPv4++;
        }
        InetAddress[] newAddressList = new InetAddress[numberOfIPv4];
        for(i=0;i<addressList.length;i++){
            if(isIPv4(addressList[i])) newAddressList[i] = addressList[i];
        }
        return newAddressList;
    }
    /*
     * @function : isIPv4
     * @argument : an IPaddress
     * @details  : returns true if the given address is an IPv4 address
     */
    boolean isIPv4(InetAddress address){return address.getAddress().length == 4;}

    // Additional Functions (from ProxyServer Part-2)
    /*
     * @function    : byteToAsciiInt(byte b)
     * @description : takes a byte of an ascii value and returns its digit if its ascii value isDigit, else returns -1
     */
    int byteToAsciiInt(byte b){
        int a = ((char)(b&0xFF));
        return (48<=a && a<=57)?((int)a)-48:-1;
    }

    /*
     * @function    : hexToAscii
     * @description : for URL parsing in FTP
     */
    public byte hexToAscii (byte[] hex){
        int dec = 0;
        for(int i=0;i<hex.length;i++){
            if('0'<=hex[i] && hex[i]<='9')
                dec = (16*dec) +(((int)hex[i])-'0');
            else if('a'<=hex[i] && hex[i]<='f')
                dec = (16*dec) +(((int)hex[i])-'a'+10);
            else if('A'<=hex[i] && hex[i]<='F')
                dec = (16*dec) +(((int)hex[i])-'A'+10);
        }
        return (byte)dec;
    }
    public boolean isHex(byte b){
        return ('0'<=b && b<='9')||('a'<=b&&b<='f')||('A'<=b&&b<='F');
    }

    /*
     * @function    : matchBytes
     * @description : search for appearance of pattern in byteArray from a given Index
     *                (also performs match on case-insensitive by lower case pattern only)
     * @returns     : if found return next index after search else returns index (no change)
     */
    int matchBytes(byte[] byteArray, byte[] pattern, boolean isLowercase , int fromIndex, boolean onlyStartBytes){
        int matchCount=0, i=0, j=0;
        byte b;
        if( (byteArray.length-fromIndex) >= pattern.length ) {
            for (i=fromIndex ; i<byteArray.length ; i++) {
                matchCount=0;
                for(j=i; j<byteArray.length && matchCount<pattern.length;j++) {
                    b = byteArray[j];
                    if(65<=b&&b<=90 && isLowercase){ // if uppercase and match case-insensitively by lowercase is set true
                        b+=32; //Convert to Lowercase
                    }
                    if (b == pattern[matchCount]) matchCount++;
                    else break;
                }
                if(matchCount==(pattern.length)) return j;
                else{
                    if(onlyStartBytes) break;
                }
            }
        }
        return fromIndex;
    }

    /*
     * @function    : concatBytes
     * @description : concat b0 and b1 array and return the result in new array
     */
    byte[] concatBytes(byte[] b0, byte[] b1){
        byte[] bytes = new byte[b0.length+b1.length];
        for(int i=0;i<b0.length;i++) bytes[i] = b0[i];
        for(int i=0;i<b1.length;i++) bytes[b0.length+i] = b1[i];
        return bytes;
    }

    /*
     * @function   : parseHeader
     * @description: parse and check if the header are of proper syntax, returns header or null
     *               => ignores those headers that are not in proper header line syntax
     *               => removes header to make http implementation process as compliant as posible
     */
    byte[] parseHeader(byte[] headerLine){
        byte[] headerField = null;
        int i,index;
        if(matchBytes(headerLine, new byte[]{'c', 'o', 'n', 'n', 'e', 'c', 't', 'i', 'o', 'n', ':', ' '}, true, 0, true)!=0) {
            this.ignoreHopHop = headerLine;
            return null;
        }for(i=0;i<ignoreHeader.length;i++) {
            if (matchBytes(headerLine, ignoreHeader[i], true, 0, true)!=0) {
                return null;
            }
        }

        for(i=0;i< headerLine.length;i++){
            if((index = matchBytes(headerLine, new byte[]{':',' '} , true, 0, false))==0){
                return null;
            }
            headerField = extractBytes(headerLine,0,index-2);
        }
        if(this.ignoreHopHop!=null && headerField!=null && headerField.length>0){
          if(matchBytes(ignoreHopHop, headerField, true, 0, false)!=0){
              return null;
          }
        }
        return headerLine;
    }

    boolean isUnstructured(byte[] filename){
         byte[][] extensions = new byte[][]{new byte[] {'.', 't', 'i', 'f'}, new byte[] {'.', 'b', 'm', 'p'}, new byte[] {'.', 'j', 'p', 'g'}, new byte[] {'.', 'j', 'p', 'e', 'g'}, new byte[] {'.', 'g', 'i', 'f'}, new byte[] {'.', 'p', 'n', 'g'}, new byte[] {'.', 'e', 'p', 's'}, new byte[] {'.', 'r', 'a', 'w'}, new byte[] {'.', 'c', 'r', '2'}, new byte[] {'.', 'n', 'e', 'f'}, new byte[] {'.', 'o', 'r', 'f'}, new byte[] {'.', 's', 'r', '2'}, new byte[] {'.', 'a', 'i', 'f'}, new byte[] {'.', 'c', 'd', 'a'}, new byte[] {'.', 'm', 'i', 'd'}, new byte[] {'.', 'm', 'p', '3'}, new byte[] {'.', 'm', 'i', 'd', 'i'}, new byte[] {'.', 'm', 'p', 'a'}, new byte[] {'.', 'o', 'g', 'g'}, new byte[] {'.', 'w', 'a', 'v'}, new byte[] {'.', 'w', 'm', 'a'}, new byte[] {'.', 'w', 'p', 'l'}, new byte[] {'.', '7', 'z'}, new byte[] {'.', 'a', 'r', 'j'}, new byte[] {'.', 'd', 'e', 'b'}, new byte[] {'.', 'p', 'k', 'g'}, new byte[] {'.', 'r', 'a', 'r'}, new byte[] {'.', 'r', 'p', 'm'}, new byte[] {'.', 'z'}, new byte[] {'.', 'z', 'i', 'p'}, new byte[] {'.', 'b', 'i', 'n'}, new byte[] {'.', 'd', 'm', 'g'}, new byte[] {'.', 'i', 's', 'o'}, new byte[] {'.', 't', 'o', 'a', 's', 't'},new byte[]{'.','t','a','r'}, new byte[] {'.', 'v', 'c', 'd'},new byte[] {'.', 'd', 'o', 'c'}, new byte[] {'.', 'o', 'd', 't'}, new byte[] {'.', 'p', 'd', 'f'}, new byte[] {'.', 'x', 'l', 's'}, new byte[] {'.', 'o', 'd', 's'}, new byte[] {'.', 'p', 'p', 't'}};
    	for(int i=0;i<extensions.length;i++){
	   if(matchBytes(filename, extensions[i], true, 0, false)!=0){
	      return true;
	   }
	}
    	return false;
    }
}

