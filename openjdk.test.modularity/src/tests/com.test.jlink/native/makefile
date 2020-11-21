# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
# 
# Usage: gmake [target] [SRC=basename] [PLATFORM=platform] [OUTDIR=destdir] [JAVA_HOME=javadir] [HEADERDIR=headerdir]
#		 basename - The name of the C file to compile without the .c extension
#        target -   The make target valid values for this makefile are build and clean
#                   If no target is supplied the build target is used.
#        platform - The platform to build for.  The valid values for this argument are
#                   aix_ppc-32, aix_ppc-64, linux_390-31, linux_390-64, linux_ppc-32
#                   linux_ppc-64, linux_x86-32, linux_x86-64, win_x86-32, win_x86-64
#                   zos_390-32, zos_390-64
#        destdir  - The directory where a platform directory will be created and the shared library will be placed 
#        javadir  - The java.home property directory (ie c:\sdk\jre)
#        headerdir -The directory containing the header files.



.SUFFIXES: .c
WIN=0

CC=gcc
LD=gcc

PREFIX=lib
SUFFIX=.so
OSUFFIX=.o

MV=mv
MKDIR=mkdir -p
RMDIR=rm -rf
RM=rm -rf

# chmod might return non zero if there is a file or directory the current user doesn't own,
# so tell make to ignore failures
CHMOD=- chmod

DESTDIR=$(OUTDIR)/$(PLATFORM)
OBJDIR=$(HEADERDIR)/$(PLATFORM)

D=/
P=:

###
# Check platform is set to a single valid value
###
VALID_PLATFORMS?=osx_x86-64,aix_ppc-32,aix_ppc-64,linux_390-32,linux_390-64,linux_ppc-32,linux_ppc-64,linux_x86-32,linux_x86-64,win_x86-32,win_x86-64,zos_390-32,zos_390-64,linux_ppcle-64,linux_arm-32,linux_arm-64,bsd_x86-64,linux_riscv64-64
ifndef PLATFORM
  $(error "The variable PLATFORM needs to be defined")
endif
ifndef OSNAME
  $(error "The variable OSNAME needs to be defined")
endif
space:=$(space) $(space)
comma:=,
ifneq "1" "$(words $(subst $(comma), ,$(PLATFORM)))"
  $(error "The variable PLATFORM should only contain one of $(VALID_PLATFORMS)")
endif
ifneq "" "$(filter-out $(subst $(comma), ,$(VALID_PLATFORMS)),$(PLATFORM))"
  $(error "The variable PLATFORM=$(PLATFORM) is not one of $(VALID_PLATFORMS)")
endif

CFLAGS=-D_JNI_IMPLEMENTATION_ -D_TRIVIAL_AGENT -O0 -g3 -pedantic -c -Wall -std=c99 -fPIC -fno-omit-frame-pointer -static-libgcc -o $(OBJDIR)/$(SRC)$(OSUFFIX)
LFLAGS=-shared -o 
# Cater for JAVA_HOME being set either to the jre dir or the parent directory
IFLAGS=-I. -I$(HEADERDIR) -I$(JAVA_HOME)/include/${OSNAME} -I$(JAVA_HOME)/include -I/usr/include

RMLIST=*.o

ifeq ($(PLATFORM),linux_x86-32)
	CFLAGS=-D_JNI_IMPLEMENTATION_ -D_TRIVIAL_AGENT -O0 -g3 -pedantic -c -Wall -std=c99 -fPIC -fno-omit-frame-pointer -static-libgcc -m32 -o $(OBJDIR)/$(SRC)$(OSUFFIX)
	LFLAGS=-shared -m32 -o 
endif

ifeq ($(PLATFORM),linux_x86-64)
	CFLAGS=-D_JNI_IMPLEMENTATION_ -D_TRIVIAL_AGENT -O0 -g3 -pedantic -c -Wall -std=c99 -fPIC -fno-omit-frame-pointer -static-libgcc -m64 -o $(OBJDIR)/$(SRC)$(OSUFFIX)
	LFLAGS=-shared -m64 -o 
endif

ifeq ($(PLATFORM),linux_ppc-32)
    CFLAGS=-D_JNI_IMPLEMENTATION_ -D_TRIVIAL_AGENT -O0 -g3 -pedantic -c -Wall -std=c99 -fPIC -fno-omit-frame-pointer -static-libgcc -m32 -o $(OBJDIR)/$(SRC)$(OSUFFIX)
    LFLAGS=-L. -L../bin/$(PLATFORM) -m32 -shared -o 
endif

ifeq ($(PLATFORM),linux_ppc-64)
    CFLAGS=-D_JNI_IMPLEMENTATION_ -D_TRIVIAL_AGENT -O0 -g3 -pedantic -c -Wall -std=c99 -fPIC -fno-omit-frame-pointer -static-libgcc -m64 -mcpu=powerpc64 -o $(OBJDIR)/$(SRC)$(OSUFFIX)
    LFLAGS=-L. -L../bin/$(PLATFORM) -m64 -shared -o 
endif

ifeq ($(PLATFORM),linux_ppcle-64)
    CFLAGS=-D_JNI_IMPLEMENTATION_ -D_TRIVIAL_AGENT -O0 -g3 -pedantic -c -Wall -std=c99 -fPIC -fno-omit-frame-pointer -static-libgcc -m64 -mcpu=powerpc64 -o $(OBJDIR)/$(SRC)$(OSUFFIX)
    LFLAGS=-L. -L../bin/$(PLATFORM) -m64 -shared -o 
endif

ifeq ($(PLATFORM),linux_390-32)
    CFLAGS=-D_JNI_IMPLEMENTATION_ -D_TRIVIAL_AGENT -O0 -g3 -pedantic -c -Wall -std=c99 -fPIC -fno-omit-frame-pointer -static-libgcc -m31 -o $(OBJDIR)/$(SRC)$(OSUFFIX)
    LFLAGS=-m31 -shared -o
endif

ifeq ($(PLATFORM),linux_arm-32)
	CFLAGS=-D_JNI_IMPLEMENTATION_ -D_TRIVIAL_AGENT -O0 -g3 -pedantic -c -Wall -std=c99 -fPIC -fno-omit-frame-pointer -static-libgcc -o $(OBJDIR)/$(SRC)$(OSUFFIX)
	LFLAGS=-shared -o 
endif

ifeq ($(PLATFORM),linux_arm-64)
	CFLAGS=-D_JNI_IMPLEMENTATION_ -D_TRIVIAL_AGENT -O0 -g3 -pedantic -c -Wall -std=c99 -fPIC -fno-omit-frame-pointer -static-libgcc -o $(OBJDIR)/$(SRC)$(OSUFFIX)
	LFLAGS=-shared -o 
endif

ifeq ($(PLATFORM),osx_x86-64)
    SUFFIX=.dylib
    CFLAGS=-D_JNI_IMPLEMENTATION_ -D_TRIVIAL_AGENT -O0 -g3 -pedantic -c -Wall -std=c99 -fPIC -fno-omit-frame-pointer -m64 -o $(OBJDIR)/$(SRC)$(OSUFFIX)
    LFLAGS=-m64 -dynamiclib -o
    IFLAGS=-I. -I$(HEADERDIR) -I$(JAVA_HOME)/include/darwin -I$(JAVA_HOME)/include -I/usr/include
endif
ifeq ($(PLATFORM),win_x86-32)
    DESTDIR=$(OUTDIR)\$(PLATFORM)
    WIN=1
endif

ifeq ($(PLATFORM),win_x86-64)
    DESTDIR=$(OUTDIR)\$(PLATFORM)
    WIN=1
endif
 
ifeq ($(PLATFORM),bsd_x86-64)
    CC=cc
    LD=cc
    IFLAGS=-I. -I$(HEADERDIR) -I$(JAVA_HOME)/include/$(shell uname | tr "[:upper:]" "[:lower:]") -I$(JAVA_HOME)/include -I/usr/include
    CFLAGS=-D_JNI_IMPLEMENTATION_ -D_TRIVIAL_AGENT -O0 -g3 -pedantic -c -Wall -std=c99 -fPIC -fno-omit-frame-pointer -m64 -o $(OBJDIR)/$(SRC)$(OSUFFIX)
    LFLAGS=-shared -m64 -o
endif

ifeq ($(WIN),1)

    # Environment variable OSTYPE is set to cygwin if running under cygwin.
    # Set our own macro to indicate we're running under cygwin.
    # Also Windows commands will need to be prefixed with cmd /c to run them in a Windows shell
    ifndef CYGWIN
      OSTYPE?=$(shell echo $$OSTYPE)
      ifeq ($(OSTYPE),cygwin)
        CYGWIN:=1
      else
        CYGWIN:=0
      endif
    endif

    ifeq (0,$(CYGWIN))
      # mkdir in Windows won't work if the path contains forward slashes
      DESTDIR := $(subst /,\,$(DESTDIR))
      OBJDIR := $(subst /,\,$(OBJDIR))
      # and let's escape backslashes in case one is stripped by the shell
      DESTDIR := $(subst \,\\,$(DESTDIR))
      OBJDIR := $(subst \,\\,$(OBJDIR))
      MV=move
      MKDIR=mkdir
      RMDIR=rmdir
      RM=del /Q
      D=\\
      P=;
    else
      # cygwin works with forward slashes
      DESTDIR := $(subst \,/,$(DESTDIR))
      OBJDIR := $(subst \,/,$(OBJDIR))
      # Prefix Windows commands with cmd /c to run them in a Windows shell
      CMD_PREFIX:=cmd /c
    endif
    
    CC=cl
    LD=link
    
    PREFIX=
    SUFFIX=.dll
    OSUFFIX=.obj
    ESUFFIX=.exe

    CFLAGS=/DWIN32 /D_WINDOWS -Gy /LD /Zi /Odi /c /RTC1 /Fo"$(OBJDIR)/$(SRC)$(OSUFFIX)" /Fd"$(OBJDIR)/$(PLATFORM)"
    LFLAGS=/NOLOGO /DLL /INCREMENTAL:NO /NODEFAULTLIB:LIBCMTD /OUT:
	# Cater for JAVA_HOME being set either to the jre dir or the parent directory
    IFLAGS=/I. /I"$(HEADERDIR)" /I"$(JAVA_HOME)/../include" /I"$(JAVA_HOME)/../include/win32" /I"$(JAVA_HOME)/include" /I"$(JAVA_HOME)/include/win32"

    RMLIST=*.exp *.lib *.obj
endif

ifeq ($(PLATFORM),zos_390-64)
    CC=c89
    LD=c89

    CFLAGS=-Wc,lp64,warn64 -W c,exportall -W c,exportall -D_JNI_IMPLEMENTATION -D_TRIVIAL_AGENT -W "c,langlvl(extended)" -W "c,float(ieee)" -W "c,c,convlit(ISO8859-1)" -DZOS -c -o $(OBJDIR)/$(SRC)$(OSUFFIX)
    LFLAGS=-Wl,lp64,amode=64 -W l,XPLINK,dll -o 
    #IFLAGS=-I. -I$(HEADERDIR) -I$(JAVA_HOME)/include -I/usr/include
    RMLIST=*.o *.x
endif

ifeq ($(PLATFORM),zos_390-32)
    CC=c89
    LD=c89

    CFLAGS=-W c,exportall -D_JNI_IMPLEMENTATION -D_TRIVIAL_AGENT -W "c,langlvl(extended)" -W "c,float(ieee)" -W "c,convlit(ISO8859-1)" -W "c,xplink,dll" -W "l,xplink,dll" -DZOS -c -o $(OBJDIR)/$(SRC)$(OSUFFIX)
    LFLAGS=-W l,XPLINK,dll -o 
    #IFLAGS=-I. -I$(HEADERDIR) -I$(JAVA_HOME)/include -I/usr/include
    RMLIST=*.o *.x
endif

ifeq ($(PLATFORM),aix_ppc-64)
    CC=xlC
    LD=xlC

    CFLAGS=-D_JNI_IMPLEMENTATION -D_TRIVIAL_AGENT -qnooptimize -g -qlanglvl=stdc99 -q64 -c -o $(OBJDIR)/$(SRC)$(OSUFFIX)
    LFLAGS=-G -q64 -o 
endif

ifeq ($(PLATFORM),aix_ppc-32)
    CC=xlC
    LD=xlC

    CFLAGS=-D_JNI_IMPLEMENTATION -D_TRIVIAL_AGENT -qnooptimize -g -qlanglvl=stdc99 -q32 -c -o $(OBJDIR)/$(SRC)$(OSUFFIX)
    LFLAGS=-G -q32 -o 
endif

##########################################
# Find the java executable under JAVA_HOME
##########################################

ifeq ($(WIN),1)
  file_exists = $(shell $(CMD_PREFIX) if exist $(1) echo file exists)
else
  file_exists = $(shell if [ -f  $(1) ] ; then echo file exists; fi;)
endif

ifeq (file exists,$(call file_exists,$(JAVA_HOME)/bin/java$(ESUFFIX)))
  JAVA_EXECUTABLE=$(JAVA_HOME)/bin/java/$(ESUFFIX)
endif

ifeq (file exists,$(call file_exists,$(JAVA_HOME)/jre/bin/java$(ESUFFIX)))
  JAVA_EXECUTABLE=$(JAVA_HOME)/jre/bin/java$(ESUFFIX)
endif

$(warning JAVA_EXECUTABLE set to $(JAVA_EXECUTABLE))

build: jni

jni: $(DESTDIR) $(OBJDIR) $(DESTDIR)/$(PREFIX)$(SRC)$(SUFFIX)

####################################################################
# Build shared library and make sure what was built runs ok
# The test command line was extracted from a run of Axxon_jni_fv.pl,
# But the invoked test class changed to the individual test rather
# than the FVRunner test suite class.
####################################################################

$(DESTDIR)/$(PREFIX)$(SRC)$(SUFFIX): $(OBJDIR)/$(SRC)$(OSUFFIX)
# Build the shared library
ifeq ($(PLATFORM),osx_x86-64)
	$(CMD_PREFIX) $(LD) $(LFLAGS) $(DESTDIR)/$(PREFIX)$(SRC)$(SUFFIX) $(OBJDIR)/$(SRC)$(OSUFFIX)
else
	$(CMD_PREFIX) $(LD) $(LFLAGS)$(DESTDIR)/$(PREFIX)$(SRC)$(SUFFIX) $(OBJDIR)/$(SRC)$(OSUFFIX)
endif
# chmod might return non zero if there is a file or directory the current user doesn't own,
# so tell make to ignore failures
ifneq ($(WIN),1)
	$(CHMOD) 755 $@
endif

########################################################################################
# Compile the tests 
########################################################################################

$(OBJDIR)/$(SRC)$(OSUFFIX): $(SRC).c $(HEADERDIR)/adoptopenjdk_test_modularity_jlink_$(SRC).h
	$(CMD_PREFIX) $(CC) $(CFLAGS) $(IFLAGS) $(SRC).c
ifneq ($(WIN),1)
	$(CHMOD) 755 $@
endif

$(DESTDIR):
	$(MKDIR) $(DESTDIR)
ifneq ($(WIN),1)
	$(CHMOD) 755 $(DESTDIR)
endif

$(OBJDIR):
	$(MKDIR) $(OBJDIR)
ifneq ($(WIN),1)
	$(CHMOD) 755 $(OBJDIR)
endif

# Not all source files have a corresponding header files
# This dummy target means that if there is a header file and it is out of date then the source
# will be recompiled.
# If there is no header file the build will continue (and will fail if there should actually be a header file).
$(HEADERDIR)/adoptopenjdk_test_modularity_jlink_$(SRC).h:
	echo $(HEADERDIR)/adoptopenjdk_test_modularity_jlink_$(SRC).h is out of date or not present

clean:
	$(RMDIR) $(OUTDIR)/$(PLATFORM)
	$(RMDIR) $(HEADERDIR)/$(PLATFORM)
