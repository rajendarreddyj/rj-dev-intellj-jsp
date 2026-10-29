<html>
<body>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<p>Before include</p>
<jsp:directive.taglib uri="jakarta.tags.core" prefix="jt"/>
<jsp:include page="/fragment.jsp"/>
${user.name}
<% int count = 1; %>
</body>
</html>
