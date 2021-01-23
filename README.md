<h1>Proxy-Server</h1>
<hr/>
<h2>Description </h2>
<p>HTTP and FTP Proxy server based on RFC-1945, RFC-2616, RFC-959 in Java Language</p>
<strong>Note: This Proxy only works with HTTP 1.0, HTTP 1.1 and FTP request messages</strong>
<hr/>
<h2>What should I do before using ProxyServer</h2>
<ol><p>Following are the steps in order to setup commonly used browsers:</p>
  <li>
    Mozilla:<br/> 
    Goto Settings &#x2190; Click Advanced(at bottom-most & center of General page) &#x2190; Click Settings &#x2190; Update you IP address and port to ProxyServer's IPaddress and port
  </li>
  <li>
    Chrome / other browsers:<br/> 
    Goto Settings &#x2190; Network Settings(at bottom-most & center of page) &#x2190; Click Open your computer's proxy settings &#x2190; Update you IP address and port to ProxyServer's IPaddress and port
</li>
</ol>
<hr/>
<h2>How to Use?</h2>
<p>There is no dependency package for using Proxy-Server. You only require Java with JDK version greater or equal to Java-8</p>
<ol><em>Steps:</em>
  <li><code>$javac ProxyServer.java</code></li>
  <li>
    <code>$java ProxyServer {port}</code><br/>
    where <em>port is integer</em> and <em>1024 &lt;= port &lt;= 65535</em><br/>
    Here, <em>port</em> means the port number on which you would like to receive the request from apache 
  </li>
</ol>
<hr/>
<h3 align='center'>Happy Using</h3>
