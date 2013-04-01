<%@ page language="java" contentType="text/html; charset=US-ASCII"
    pageEncoding="US-ASCII"%>
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=US-ASCII">
<title>CS5300 - assign2</title>
<style type="text/css">

* {
	margin: 4px;
	padding: 4px;
	font: 10pt helvetica, arial;
}

.error {
	font-weight: bold;
	color: #ff0000;
}

</style>
</head>
<body>

<div class="error">
${error}
</div>

<div id="data">
SessionID: ${sid} <br/>
Version: ${version} <br/>
Message: ${message} <br/>
Served by: ${serverID} <br/>
Session State Retrieved from: ${foundOn} <br/>
IPP_primary: ${IPPprimary} <br/>
IPP_backup: ${IPPbackup} <br/>
Expires: ${expiry} <br/>
Discard Time: ${discard_time} <br/>
Member Set: ${MbrSet} <br/>
Hashmap entries: ${hashmap} <br/>
</div>

<form method="GET" action="Servlet">
<input type="submit" name="cmd" value="Replace">&nbsp;&nbsp;<input type="text" name="NewText" id="NewText" size="40" maxlength="512">&nbsp;&nbsp;
</form>
<form method="GET" action="Servlet">
<input type="submit" name="cmd" value="Refresh">
</form>
<form method="GET" action="Servlet">
<input type="submit" name="cmd" value="LogOut">
</form>
<form method ="GET" action="Servlet">
<input type="submit" name="cmd" value="Crash">
</form>


</body>
</html>