@echo OFF
echo.
echo ******************************************************
echo *	Created By: Circuit Negma		     *
echo *	Date	  : 02/01/2007			     *
echo *	File	  : Install_javaxComm.bat	     *
echo *	WebSite	  : http://circuitnegma.wordpress.com*
echo ******************************************************
echo.
echo INSTALLING...
echo.
echo.

echo [-- win32com.dll --] is being installed to Directory: "C:\Program Files\Java\jdk1.5.0_10\jre\bin"
copy win32com.dll C:\Program Files\Java\jdk1.5.0_10\jre\bin
echo [-- win32com.dll --] File is intalled

echo [-- win32com.dll --] is being installed to Directory: "C:\WINDOWS\system32"
copy win32com.dll C:\WINDOWS\system32
echo [-- win32com.dll --] File is intalled

echo.
echo [-- comm,jar --] is being installed to Directory: "C:\Program Files\Java\jdk1.5.0_10\jre\lib\ext"
copy comm.jar C:\Program Files\Java\jdk1.5.0_10\jre\lib\ext
echo [-- comm.jar --] File is intalled

echo.
echo [-- javax.comm.properties --] is being installed to Directory: "C:\Program Files\Java\jdk1.5.0_10\jre\lib"
copy javax.comm.properties C:\Program Files\Java\jdk1.5.0_10\jre\lib
echo [-- javax.comm.properties --] File is intalled

echo.
echo.
echo DONE!
echo.
echo SIGNING OFF
