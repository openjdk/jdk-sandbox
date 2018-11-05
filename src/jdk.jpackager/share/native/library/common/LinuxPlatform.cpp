/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

#include "Platform.h"

#ifdef LINUX

#include "JavaVirtualMachine.h"
#include "LinuxPlatform.h"
#include "PlatformString.h"
#include "IniFile.h"
#include "Helpers.h"

#include <stdlib.h>
#include <pwd.h>
#include <sys/file.h>
#include <sys/stat.h>
#include <errno.h>
#include <unistd.h>
#include <sys/types.h>
#include <limits.h>

#define LINUX_JPACKAGER_TMP_DIR "/.java/jpackager/tmp"


TString GetEnv(const TString &name) {
    TString result;

    char *value = ::getenv((TCHAR*)name.c_str());

    if (value != NULL) {
       result = value;
    }

    return result;
}

LinuxPlatform::LinuxPlatform(void) : Platform(),
        GenericPlatform(), PosixPlatform() {
    FMainThread = pthread_self();
}

LinuxPlatform::~LinuxPlatform(void) {
}

void LinuxPlatform::ShowMessage(TString title, TString description) {
    printf("%s %s\n", PlatformString(title).toPlatformString(),
            PlatformString(description).toPlatformString());
    fflush(stdout);
}

void LinuxPlatform::ShowMessage(TString description) {
    TString appname = GetModuleFileName();
    appname = FilePath::ExtractFileName(appname);
    ShowMessage(PlatformString(appname).toPlatformString(),
            PlatformString(description).toPlatformString());
}

TCHAR* LinuxPlatform::ConvertStringToFileSystemString(TCHAR* Source,
         bool &release) {
    // Not Implemented.
    return NULL;
}

TCHAR* LinuxPlatform::ConvertFileSystemStringToString(TCHAR* Source,
         bool &release) {
    // Not Implemented.
    return NULL;
}

TString LinuxPlatform::GetModuleFileName() {
    ssize_t len = 0;
    TString result;
    DynamicBuffer<TCHAR> buffer(MAX_PATH);
    if (buffer.GetData() == NULL) {
        return result;
    }

    if ((len = readlink("/proc/self/exe", buffer.GetData(),
            MAX_PATH - 1)) != -1) {
        buffer[len] = '\0';
        result = buffer.GetData();
    }

    return result;
}

void LinuxPlatform::SetCurrentDirectory(TString Value) {
    chdir(PlatformString(Value).toPlatformString());
}

TString LinuxPlatform::GetPackageRootDirectory() {
    TString filename = GetModuleFileName();
    return FilePath::ExtractFilePath(filename);
}

TString LinuxPlatform::GetAppDataDirectory() {
    TString result;
    TString home = GetEnv(_T("HOME"));

    if (home.empty() == false) {
        result += FilePath::IncludeTrailingSeparator(home) + _T(".local");
    }

    return result;
}

ISectionalPropertyContainer* LinuxPlatform::GetConfigFile(TString FileName) {
    IniFile *result = new IniFile();
    if (result == NULL) {
        return NULL;
    }

    if (result->LoadFromFile(FileName) == false) {
        // New property file format was not found,
        // attempt to load old property file format.
        Helpers::LoadOldConfigFile(FileName, result);
    }

    return result;
}

TString LinuxPlatform::GetBundledJVMLibraryFileName(TString RuntimePath) {
    TString result = FilePath::IncludeTrailingSeparator(RuntimePath) +
        "lib/libjli.so";

    if (FilePath::FileExists(result) == false) {
        result = FilePath::IncludeTrailingSeparator(RuntimePath) +
            "lib/jli/libjli.so";
        if (FilePath::FileExists(result) == false) {
            printf("Cannot find libjli.so!");
        }
    }

    return result;
}

bool LinuxPlatform::IsMainThread() {
    bool result = (FMainThread == pthread_self());
    return result;
}

TString LinuxPlatform::getTmpDirString() {
    return TString(LINUX_JPACKAGER_TMP_DIR);
}

void LinuxPlatform::reactivateAnotherInstance() {
    if (singleInstanceProcessId == 0) {
        printf("Unable to reactivate another instance, PID is undefined");
        return;
    }

    const ProcessReactivator reactivator(singleInstanceProcessId);
}

TPlatformNumber LinuxPlatform::GetMemorySize() {
    long pages = sysconf(_SC_PHYS_PAGES);
    long page_size = sysconf(_SC_PAGE_SIZE);
    TPlatformNumber result = pages * page_size;
    result = result / 1048576; // Convert from bytes to megabytes.
    return result;
}

#ifdef DEBUG
bool LinuxPlatform::IsNativeDebuggerPresent() {
    // gdb opens file descriptors stdin=3, stdout=4, stderr=5 whereas
    // a typical prog uses only stdin=0, stdout=1, stderr=2.
    bool result = false;
    FILE *fd = fopen("/tmp", "r");

    if (fileno(fd) > 5) {
        result = true;
    }

    fclose(fd);
    return result;
}

int LinuxPlatform::GetProcessID() {
    int pid = getpid();
    return pid;
}
#endif //DEBUG

//----------------------------------------------------------------------------

#ifndef __UNIX_JPACKAGER_PLATFORM__
#define __UNIX_JPACKAGER_PLATFORM__

/** Provide an abstraction for difference in the platform APIs,
     e.g. string manipulation functions, etc. */
#include <stdio.h>
#include <string.h>
#include <strings.h>
#include <sys/stat.h>

#define TCHAR char

#define _T(x) x

#define JPACKAGER_MULTIBYTE_SNPRINTF snprintf

#define JPACKAGER_SNPRINTF(buffer, sizeOfBuffer, count, format, ...) \
    snprintf((buffer), (count), (format), __VA_ARGS__)

#define JPACKAGER_PRINTF(format, ...) \
    printf((format), ##__VA_ARGS__)

#define JPACKAGER_FPRINTF(dest, format, ...) \
    fprintf((dest), (format), __VA_ARGS__)

#define JPACKAGER_SSCANF(buf, format, ...) \
    sscanf((buf), (format), __VA_ARGS__)

#define JPACKAGER_STRDUP(strSource) \
    strdup((strSource))

//return "error code" (like on Windows)
static int JPACKAGER_STRNCPY(char *strDest, size_t numberOfElements,
        const char *strSource, size_t count) {
    char *s = strncpy(strDest, strSource, count);
    // Duplicate behavior of the Windows' _tcsncpy_s() by adding a NULL
    // terminator at the end of the string.
    if (count < numberOfElements) {
        s[count] = '\0';
    } else {
        s[numberOfElements - 1] = '\0';
    }
    return (s == strDest) ? 0 : 1;
}

#define JPACKAGER_STRICMP(x, y) \
    strcasecmp((x), (y))

#define JPACKAGER_STRNICMP(x, y, cnt) \
    strncasecmp((x), (y), (cnt))

#define JPACKAGER_STRNCMP(x, y, cnt) \
    strncmp((x), (y), (cnt))

#define JPACKAGER_STRLEN(x) \
    strlen((x))

#define JPACKAGER_STRSTR(x, y) \
    strstr((x), (y))

#define JPACKAGER_STRCHR(x, y) \
    strchr((x), (y))

#define JPACKAGER_STRRCHR(x, y) \
    strrchr((x), (y))

#define JPACKAGER_STRPBRK(x, y) \
    strpbrk((x), (y))

#define JPACKAGER_GETENV(x) \
    getenv((x))

#define JPACKAGER_PUTENV(x) \
    putenv((x))

#define JPACKAGER_STRCMP(x, y) \
    strcmp((x), (y))

#define JPACKAGER_STRCPY(x, y) \
    strcpy((x), (y))

#define JPACKAGER_STRCAT(x, y) \
    strcat((x), (y))

#define JPACKAGER_ATOI(x) \
    atoi((x))

#define JPACKAGER_FOPEN(x, y) \
    fopen((x), (y))

#define JPACKAGER_FGETS(x, y, z) \
    fgets((x), (y), (z))

#define JPACKAGER_REMOVE(x) \
    remove((x))

#define JPACKAGER_SPAWNV(mode, cmd, args) \
    spawnv((mode), (cmd), (args))

#define JPACKAGER_ISDIGIT(ch) isdigit(ch)

// for non-unicode, just return the input string for
// the following 2 conversions
#define JPACKAGER_NEW_MULTIBYTE(message) message

#define JPACKAGER_NEW_FROM_MULTIBYTE(message) message

// for non-unicode, no-op for the relase operation
// since there is no memory allocated for the
// string conversions
#define JPACKAGER_RELEASE_MULTIBYTE(tmpMBCS)

#define JPACKAGER_RELEASE_FROM_MULTIBYTE(tmpMBCS)

// The size will be used for converting from 1 byte to 1 byte encoding.
// Ensure have space for zero-terminator.
#define JPACKAGER_GET_SIZE_FOR_ENCODING(message, theLength) (theLength + 1)

#endif
#define xmlTagType    0
#define xmlPCDataType 1

typedef struct _xmlNode XMLNode;
typedef struct _xmlAttribute XMLAttribute;

struct _xmlNode {
    int           _type;        // Type of node: tag, pcdata, cdate
    TCHAR*         _name;       // Contents of node
    XMLNode*      _next;        // Next node at same level
    XMLNode*      _sub;         // First sub-node
    XMLAttribute* _attributes;  // List of attributes
};

struct _xmlAttribute {
    TCHAR* _name;               // Name of attribute
    TCHAR* _value;              // Value of attribute
    XMLAttribute* _next;        // Next attribute for this tag
};

// Public interface
static void     RemoveNonAsciiUTF8FromBuffer(char *buf);
XMLNode* ParseXMLDocument    (TCHAR* buf);
void     FreeXMLDocument     (XMLNode* root);

// Utility methods for parsing document
XMLNode* FindXMLChild        (XMLNode* root,      const TCHAR* name);
TCHAR*    FindXMLAttribute    (XMLAttribute* attr, const TCHAR* name);

// Debugging
void PrintXMLDocument(XMLNode* node, int indt);


#include <sys/types.h>
#include <sys/stat.h>
#include <setjmp.h>
#include <stdlib.h>
#include <wctype.h>


#define JWS_assert(s, msg)      \
    if (!(s)) { Abort(msg); }


// Internal declarations
static XMLNode*      ParseXMLElement(void);
static XMLAttribute* ParseXMLAttribute(void);
static TCHAR*         SkipWhiteSpace(TCHAR *p);
static TCHAR*         SkipXMLName(TCHAR *p);
static TCHAR*         SkipXMLComment(TCHAR *p);
static TCHAR*         SkipXMLDocType(TCHAR *p);
static TCHAR*         SkipXMLProlog(TCHAR *p);
static TCHAR*         SkipPCData(TCHAR *p);
static int           IsPCData(TCHAR *p);
static void          ConvertBuiltInEntities(TCHAR* p);
static void          SetToken(int type, TCHAR* start, TCHAR* end);
static void          GetNextToken(void);
static XMLNode*      CreateXMLNode(int type, TCHAR* name);
static XMLAttribute* CreateXMLAttribute(TCHAR *name, TCHAR* value);
static XMLNode*      ParseXMLElement(void);
static XMLAttribute* ParseXMLAttribute(void);
static void          FreeXMLAttribute(XMLAttribute* attr);
static void          PrintXMLAttributes(XMLAttribute* attr);
static void          indent(int indt);

static jmp_buf       jmpbuf;
static XMLNode*      root_node = NULL;

/** definition of error codes for setjmp/longjmp,
 *  that can be handled in ParseXMLDocument()
 */
#define JMP_NO_ERROR     0
#define JMP_OUT_OF_RANGE 1

#define NEXT_CHAR(p) { \
    if (*p != 0) { \
        p++; \
    } else { \
        longjmp(jmpbuf, JMP_OUT_OF_RANGE); \
    } \
}
#define NEXT_CHAR_OR_BREAK(p) { \
    if (*p != 0) { \
        p++; \
    } else { \
        break; \
    } \
}
#define NEXT_CHAR_OR_RETURN(p) { \
    if (*p != 0) { \
        p++; \
    } else { \
        return; \
    } \
}
#define SKIP_CHARS(p,n) { \
    int i; \
    for (i = 0; i < (n); i++) { \
        if (*p != 0) { \
            p++; \
        } else { \
           longjmp(jmpbuf, JMP_OUT_OF_RANGE); \
        } \
    } \
}
#define SKIP_CHARS_OR_BREAK(p,n) { \
    int i; \
    for (i = 0; i < (n); i++) { \
        if (*p != 0) { \
            p++; \
        } else { \
            break; \
        } \
    } \
    if (i < (n)) { \
        break; \
    } \
}

/** Iterates through the null-terminated buffer (i.e., C string) and
 *  replaces all UTF-8 encoded character >255 with 255
 *
 *  UTF-8 encoding:
 *
 *   Range A:  0x0000 - 0x007F
 *                               0 | bits 0 - 7
 *   Range B : 0x0080 - 0x07FF  :
 *                               110 | bits 6 - 10
 *                               10  | bits 0 - 5
 *   Range C : 0x0800 - 0xFFFF  :
 *                               1110 | bits 12-15
 *                               10   | bits  6-11
 *                               10   | bits  0-5
 */
static void RemoveNonAsciiUTF8FromBuffer(char *buf) {
    char* p;
    char* q;
    char c;
    p = q = buf;
    // We are not using NEXT_CHAR() to check if *q is NULL, as q is output
    // location and offset for q is smaller than for p.
    while(*p != '\0') {
        c = *p;
        if ( (c & 0x80) == 0) {
            /* Range A */
            *q++ = *p;
            NEXT_CHAR(p);
        } else if ((c & 0xE0) == 0xC0){
            /* Range B */
            *q++ = (char)0xFF;
            NEXT_CHAR(p);
            NEXT_CHAR_OR_BREAK(p);
        } else {
            /* Range C */
            *q++ = (char)0xFF;
            NEXT_CHAR(p);
            SKIP_CHARS_OR_BREAK(p, 2);
        }
    }
    /* Null terminate string */
    *q = '\0';
}

static TCHAR* SkipWhiteSpace(TCHAR *p) {
    if (p != NULL) {
        while(iswspace(*p))
            NEXT_CHAR_OR_BREAK(p);
    }
    return p;
}

static TCHAR* SkipXMLName(TCHAR *p) {
    TCHAR c = *p;
    /* Check if start of token */
    if ( ('a' <= c && c <= 'z') ||
         ('A' <= c && c <= 'Z') ||
         c == '_' || c == ':') {

        while( ('a' <= c && c <= 'z') ||
               ('A' <= c && c <= 'Z') ||
               ('0' <= c && c <= '9') ||
               c == '_' || c == ':' || c == '.' || c == '-' ) {
            NEXT_CHAR(p);
            c = *p;
            if (c == '\0') break;
        }
    }
    return p;
}

static TCHAR* SkipXMLComment(TCHAR *p) {
    if (p != NULL) {
        if (JPACKAGER_STRNCMP(p, _T("<!--"), 4) == 0) {
            SKIP_CHARS(p, 4);
            do {
                if (JPACKAGER_STRNCMP(p, _T("-->"), 3) == 0) {
                    SKIP_CHARS(p, 3);
                    return p;
                }
                NEXT_CHAR(p);
            } while(*p != '\0');
        }
    }
    return p;
}

static TCHAR* SkipXMLDocType(TCHAR *p) {
    if (p != NULL) {
        if (JPACKAGER_STRNCMP(p, _T("<!"), 2) == 0) {
            SKIP_CHARS(p, 2);
            while (*p != '\0') {
                if (*p == '>') {
                    NEXT_CHAR(p);
                    return p;
                }
                NEXT_CHAR(p);
            }
        }
    }
    return p;
}

static TCHAR* SkipXMLProlog(TCHAR *p) {
    if (p != NULL) {
        if (JPACKAGER_STRNCMP(p, _T("<?"), 2) == 0) {
            SKIP_CHARS(p, 2);
            do {
                if (JPACKAGER_STRNCMP(p, _T("?>"), 2) == 0) {
                    SKIP_CHARS(p, 2);
                    return p;
                }
                NEXT_CHAR(p);
            } while(*p != '\0');
        }
    }
    return p;
}

/* Search for the built-in XML entities:
 * &amp; (&), &lt; (<), &gt; (>), &apos; ('), and &quote(")
 * and convert them to a real TCHARacter
 */
static void ConvertBuiltInEntities(TCHAR* p) {
    TCHAR* q;
    q = p;
    // We are not using NEXT_CHAR() to check if *q is NULL,
    // as q is output location and offset for q is smaller than for p.
    while(*p) {
        if (IsPCData(p)) {
            /* dont convert &xxx values within PData */
            TCHAR *end;
            end = SkipPCData(p);
            while(p < end) {
                *q++ = *p;
                NEXT_CHAR(p);
            }
        } else {
            if (JPACKAGER_STRNCMP(p, _T("&amp;"), 5) == 0) {
                *q++ = '&';
                SKIP_CHARS(p, 5);
            } else if (JPACKAGER_STRNCMP(p, _T("&lt;"), 4)  == 0) {
                *q = '<';
                SKIP_CHARS(p, 4);
            } else if (JPACKAGER_STRNCMP(p, _T("&gt;"), 4)  == 0) {
                *q = '>';
                SKIP_CHARS(p, 4);
            } else if (JPACKAGER_STRNCMP(p, _T("&apos;"), 6)  == 0) {
                *q = '\'';
                SKIP_CHARS(p, 6);
            } else if (JPACKAGER_STRNCMP(p, _T("&quote;"), 7)  == 0) {
                *q = '\"';
              SKIP_CHARS(p, 7);
            } else {
              *q++ = *p;
              NEXT_CHAR(p);
            }
        }
    }
    *q = '\0';
}

/* ------------------------------------------------------------- */
/* XML tokenizer */

#define TOKEN_UNKNOWN             0
#define TOKEN_BEGIN_TAG           1  /* <tag */
#define TOKEN_END_TAG             2  /* </tag */
#define TOKEN_CLOSE_BRACKET       3  /* >  */
#define TOKEN_EMPTY_CLOSE_BRACKET 4  /* /> */
#define TOKEN_PCDATA              5  /* pcdata */
#define TOKEN_CDATA               6  /* cdata */
#define TOKEN_EOF                 7

static TCHAR* CurPos       = NULL;
static TCHAR* CurTokenName        = NULL;
static int   CurTokenType;
static int   MaxTokenSize = -1;

/* Copy token from buffer to Token variable */
static void SetToken(int type, TCHAR* start, TCHAR* end) {
    int len = end - start;
    if (len > MaxTokenSize) {
        if (CurTokenName != NULL) free(CurTokenName);
        CurTokenName = (TCHAR *)malloc((len + 1) * sizeof(TCHAR));
        if (CurTokenName == NULL ) {
            return;
        }
        MaxTokenSize = len;
    }

    CurTokenType = type;
    JPACKAGER_STRNCPY(CurTokenName, len + 1, start, len);
    CurTokenName[len] = '\0';
}

/* Skip XML comments, doctypes, and prolog tags */
static TCHAR* SkipFilling(void) {
    TCHAR *q = CurPos;

    /* Skip white space and comment sections */
    do {
        q = CurPos;
        CurPos = SkipWhiteSpace(CurPos);
        CurPos = SkipXMLComment(CurPos); /* Must be called befor DocTypes */
        CurPos = SkipXMLDocType(CurPos); /* <! ... > directives */
        CurPos = SkipXMLProlog(CurPos);   /* <? ... ?> directives */
    } while(CurPos != q);

    return CurPos;
}

/* Parses next token and initializes the global token variables above
   The tokennizer automatically skips comments (<!-- comment -->) and
   <! ... > directives.
*/
static void GetNextToken(void) {
    TCHAR *p, *q;

    /* Skip white space and comment sections */
    p = SkipFilling();

    if (p == NULL || *p == '\0') {
        CurTokenType = TOKEN_EOF;
        return;
    } else if (p[0] == '<' && p[1] == '/') {
        /* TOKEN_END_TAG */
        q = SkipXMLName(p + 2);
        SetToken(TOKEN_END_TAG, p + 2, q);
        p = q;
    } else  if (*p == '<') {
        /* TOKEN_BEGIN_TAG */
        q = SkipXMLName(p + 1);
        SetToken(TOKEN_BEGIN_TAG, p + 1, q);
        p = q;
    } else if (p[0] == '>') {
        CurTokenType = TOKEN_CLOSE_BRACKET;
        NEXT_CHAR(p);
    } else if (p[0] == '/' && p[1] == '>') {
        CurTokenType = TOKEN_EMPTY_CLOSE_BRACKET;
        SKIP_CHARS(p, 2);
    } else {
        /* Search for end of data */
        q = p + 1;
        while(*q && *q != '<') {
            if (IsPCData(q)) {
                q = SkipPCData(q);
            } else {
                NEXT_CHAR(q);
            }
        }
        SetToken(TOKEN_PCDATA, p, q);
        /* Convert all entities inside token */
        ConvertBuiltInEntities(CurTokenName);
        p = q;
    }
    /* Advance pointer to beginning of next token */
    CurPos = p;
}

static XMLNode* CreateXMLNode(int type, TCHAR* name) {
    XMLNode* node;
    node  = (XMLNode*)malloc(sizeof(XMLNode));
    if (node == NULL) {
        return NULL;
    }
    node->_type = type;
    node->_name = name;
    node->_next = NULL;
    node->_sub  = NULL;
    node->_attributes = NULL;
    return node;
}

static XMLAttribute* CreateXMLAttribute(TCHAR *name, TCHAR* value) {
    XMLAttribute* attr;
    attr = (XMLAttribute*)malloc(sizeof(XMLAttribute));
    if (attr == NULL) {
        return NULL;
    }
    attr->_name = name;
    attr->_value = value;
    attr->_next =  NULL;
    return attr;
}

XMLNode* ParseXMLDocument(TCHAR* buf) {
    XMLNode* root;
    int err_code = setjmp(jmpbuf);
    switch (err_code)
    {
    case JMP_NO_ERROR:
#ifndef _UNICODE
        /* Remove UTF-8 encoding from buffer */
        RemoveNonAsciiUTF8FromBuffer(buf);
#endif

        /* Get first Token */
        CurPos = buf;
        GetNextToken();

        /* Parse document*/
        root =  ParseXMLElement();
    break;
    case JMP_OUT_OF_RANGE:
        /* cleanup: */
        if (root_node != NULL) {
            FreeXMLDocument(root_node);
            root_node = NULL;
        }
        if (CurTokenName != NULL) free(CurTokenName);
        fprintf(stderr,"Error during parsing jnlp file...\n");
        exit(-1);
    break;
    default:
        root = NULL;
    break;
    }

    return root;
}

static XMLNode* ParseXMLElement(void) {
    XMLNode*  node     = NULL;
    XMLNode*  subnode  = NULL;
    XMLNode*  nextnode = NULL;
    XMLAttribute* attr = NULL;

    if (CurTokenType == TOKEN_BEGIN_TAG) {

        /* Create node for new element tag */
        node = CreateXMLNode(xmlTagType, JPACKAGER_STRDUP(CurTokenName));
        /* We need to save root node pointer to be able to cleanup
           if an error happens during parsing */
        if(!root_node) {
            root_node = node;
        }
        /* Parse attributes. This section eats a all input until
           EOF, a > or a /> */
        attr = ParseXMLAttribute();
        while(attr != NULL) {
          attr->_next = node->_attributes;
          node->_attributes = attr;
          attr = ParseXMLAttribute();
        }

        /* This will eihter be a TOKEN_EOF, TOKEN_CLOSE_BRACKET, or a
         * TOKEN_EMPTY_CLOSE_BRACKET */
        GetNextToken();

        /* Skip until '>', '/>' or EOF. This should really be an error, */
        /* but we are loose */
//        if(CurTokenType == TOKEN_EMPTY_CLOSE_BRACKET ||
//               CurTokenType == TOKEN_CLOSE_BRACKET ||
//               CurTokenType  == TOKEN_EOF) {
//            println("XML Parsing error: wrong kind of token found");
//            return NULL;
//        }

        if (CurTokenType == TOKEN_EMPTY_CLOSE_BRACKET) {
            GetNextToken();
            /* We are done with the sublevel - fall through to continue */
            /* parsing tags at the same level */
        } else if (CurTokenType == TOKEN_CLOSE_BRACKET) {
            GetNextToken();

            /* Parse until end tag if found */
            node->_sub  = ParseXMLElement();

            if (CurTokenType == TOKEN_END_TAG) {
                /* Find closing bracket '>' for end tag */
                do {
                   GetNextToken();
                } while(CurTokenType != TOKEN_EOF &&
                        CurTokenType != TOKEN_CLOSE_BRACKET);
                GetNextToken();
            }
        }

        /* Continue parsing rest on same level */
        if (CurTokenType != TOKEN_EOF) {
            /* Parse rest of stream at same level */
            node->_next = ParseXMLElement();
        }
        return node;

    } else if (CurTokenType == TOKEN_PCDATA) {
        /* Create node for pcdata */
        node = CreateXMLNode(xmlPCDataType, JPACKAGER_STRDUP(CurTokenName));
        /* We need to save root node pointer to be able to cleanup
           if an error happens during parsing */
        if(!root_node) {
            root_node = node;
        }
        GetNextToken();
        return node;
    }

    /* Something went wrong. */
    return NULL;
}

/* Parses an XML attribute. */
static XMLAttribute* ParseXMLAttribute(void) {
    TCHAR* q = NULL;
    TCHAR* name = NULL;
    TCHAR* PrevPos = NULL;

    do
    {
        /* We need to check this condition to avoid endless loop
           in case if an error happend during parsing. */
        if (PrevPos == CurPos) {
            if (name != NULL) {
                free(name);
                name = NULL;
            }

            return NULL;
        }

        PrevPos = CurPos;

        /* Skip whitespace etc. */
        SkipFilling();

        /* Check if we are done witht this attribute section */
        if (CurPos[0] == '\0' ||
            CurPos[0] == '>' ||
            (CurPos[0] == '/' && CurPos[1] == '>')) {

            if (name != NULL) {
                free(name);
                name = NULL;
            }

            return NULL;
        }

        /* Find end of name */
        q = CurPos;
        while(*q && !iswspace(*q) && *q !='=') NEXT_CHAR(q);

        SetToken(TOKEN_UNKNOWN, CurPos, q);
        if (name) {
            free(name);
            name = NULL;
        }
        name = JPACKAGER_STRDUP(CurTokenName);

        /* Skip any whitespace */
        CurPos = q;
        CurPos = SkipFilling();

        /* Next TCHARacter must be '=' for a valid attribute.
           If it is not, this is really an error.
           We ignore this, and just try to parse an attribute
           out of the rest of the string.
        */
    } while(*CurPos != '=');

    NEXT_CHAR(CurPos);
    CurPos = SkipWhiteSpace(CurPos);
    /* Parse CDATA part of attribute */
    if ((*CurPos == '\"') || (*CurPos == '\'')) {
        TCHAR quoteChar = *CurPos;
        q = ++CurPos;
        while(*q != '\0' && *q != quoteChar) NEXT_CHAR(q);
        SetToken(TOKEN_CDATA, CurPos, q);
        CurPos = q + 1;
    } else {
        q = CurPos;
        while(*q != '\0' && !iswspace(*q)) NEXT_CHAR(q);
        SetToken(TOKEN_CDATA, CurPos, q);
        CurPos = q;
    }

    //Note: no need to free name and CurTokenName duplicate; they're assigned
    // to an XMLAttribute structure in CreateXMLAttribute

    return CreateXMLAttribute(name, JPACKAGER_STRDUP(CurTokenName));
}

void FreeXMLDocument(XMLNode* root) {
    if (root == NULL) return;
    FreeXMLDocument(root->_sub);
    FreeXMLDocument(root->_next);
    FreeXMLAttribute(root->_attributes);
    free(root->_name);
    free(root);
}

static void FreeXMLAttribute(XMLAttribute* attr) {
    if (attr == NULL) return;
    free(attr->_name);
    free(attr->_value);
    FreeXMLAttribute(attr->_next);
    free(attr);
}

/* Find element at current level with a given name */
XMLNode* FindXMLChild(XMLNode* root, const TCHAR* name) {
    if (root == NULL) return NULL;

    if (root->_type == xmlTagType && JPACKAGER_STRCMP(root->_name, name) == 0) {
        return root;
    }

    return FindXMLChild(root->_next, name);
}

/* Search for an attribute with the given name and returns the contents. Returns NULL if
 * attribute is not found
 */
TCHAR* FindXMLAttribute(XMLAttribute* attr, const TCHAR* name) {
    if (attr == NULL) return NULL;
    if (JPACKAGER_STRCMP(attr->_name, name) == 0) return attr->_value;
    return FindXMLAttribute(attr->_next, name);
}


void PrintXMLDocument(XMLNode* node, int indt) {
    if (node == NULL) return;

    if (node->_type == xmlTagType) {
        JPACKAGER_PRINTF(_T("\n"));
        indent(indt);
        JPACKAGER_PRINTF(_T("<%s"), node->_name);
        PrintXMLAttributes(node->_attributes);
        if (node->_sub == NULL) {
            JPACKAGER_PRINTF(_T("/>\n"));
        } else {
            JPACKAGER_PRINTF(_T(">"));
            PrintXMLDocument(node->_sub, indt + 1);
            indent(indt);
            JPACKAGER_PRINTF(_T("</%s>"), node->_name);
        }
    } else {
        JPACKAGER_PRINTF(_T("%s"), node->_name);
    }
    PrintXMLDocument(node->_next, indt);
}

static void PrintXMLAttributes(XMLAttribute* attr) {
    if (attr == NULL) return;

    JPACKAGER_PRINTF(_T(" %s=\"%s\""), attr->_name, attr->_value);
    PrintXMLAttributes(attr->_next);
}

static void indent(int indt) {
    int i;
    for(i = 0; i < indt; i++) {
        JPACKAGER_PRINTF(_T("  "));
    }
}

const TCHAR *CDStart = _T("<![CDATA[");
const TCHAR *CDEnd = _T("]]>");


static TCHAR* SkipPCData(TCHAR *p) {
    TCHAR *end = JPACKAGER_STRSTR(p, CDEnd);
    if (end != NULL) {
        return end+sizeof(CDEnd);
    }
    return (++p);
}

static int IsPCData(TCHAR *p) {
    const int size = sizeof(CDStart);
    return (JPACKAGER_STRNCMP(CDStart, p, size) == 0);
}

namespace {
    template<class funcType>
    class DllFunction {
        const Library& lib;
        funcType funcPtr;
        std::string theName;

    public:
        DllFunction(const Library& library,
                const std::string &funcName): lib(library) {
            funcPtr = reinterpret_cast<funcType>(lib.GetProcAddress(funcName));
            if (!funcPtr) {
                throw std::runtime_error("Failed to load function \""
                        + funcName + "\" from \""
                        + library.GetName() + "\" library");
            }
        }

        operator funcType() const {
            return funcPtr;
        }
    };
} // namespace

extern "C" {
typedef Status (*XInitThreadsFuncPtr)();
typedef Display* (*XOpenDisplayFuncPtr)(char *display_name);

typedef Atom (*XInternAtomFuncPtr)(
        Display *display, char *atom_name, Bool only_if_exists);

typedef Window (*XDefaultRootWindowFuncPtr)(Display *display);

typedef int (*XCloseDisplayFuncPtr)(Display *display);
}

ProcessReactivator::ProcessReactivator(pid_t pid): _pid(pid) {
    const std::string libname = "libX11.so";
    if(!libX11.Load(libname)) {
        throw std::runtime_error("Failed to load \"" + libname + "\" library");
    }

    DllFunction<XInitThreadsFuncPtr> XInitThreadsFunc(libX11, "XInitThreads");

    XInitThreadsFunc();

    DllFunction<XOpenDisplayFuncPtr> XOpenDisplayFunc(libX11, "XOpenDisplay");

    _display = XOpenDisplayFunc(NULL);

    DllFunction<XInternAtomFuncPtr> XInternAtomFunc(libX11, "XInternAtom");

    _atomPid = XInternAtomFunc(_display, (char*)"_NET_WM_PID", True);

    if (_atomPid == None) {
        return;
    }

    DllFunction<XDefaultRootWindowFuncPtr> XDefaultRootWindowFunc(libX11,
            "XDefaultRootWindow");

    searchWindowHelper(XDefaultRootWindowFunc(_display));

    reactivateProcess();

    DllFunction<XCloseDisplayFuncPtr> XCloseDisplayFunc(libX11,
            "XCloseDisplay");

    XCloseDisplayFunc(_display);
}

extern "C" {
typedef int (*XGetWindowPropertyFuncPtr)(
        Display *display, Window w, Atom property, long long_offset,
        long long_length, Bool d, Atom req_type, Atom *actual_type_return,
        int *actual_format_return, unsigned long *nitems_return,
        unsigned long *bytes_after_return, unsigned char **prop_return);

typedef Status (*XQueryTreeFuncPtr)(
        Display *display, Window w, Window *root_return, Window *parent_return,
         Window **children_return, unsigned int *nchildren_return);

typedef int (*XFreeFuncPtr)(void *data);
}

void ProcessReactivator::searchWindowHelper(Window w) {

    DllFunction<XGetWindowPropertyFuncPtr> XGetWindowPropertyFunc(libX11,
            "XGetWindowProperty");

    DllFunction<XFreeFuncPtr> XFreeFunc(libX11, "XFree");

    Atom type;
    int format;
    unsigned long  num, bytesAfter;
    unsigned char* propPid = 0;
    if (Success == XGetWindowPropertyFunc(_display, w, _atomPid, 0, 1,
            False, XA_CARDINAL, &type, &format, &num, &bytesAfter, &propPid)) {
        if (propPid != 0) {
            if (_pid == *((pid_t *)propPid)) {
                _result.push_back(w);
            }
            XFreeFunc(propPid);
        }
    }

    DllFunction<XQueryTreeFuncPtr> XQueryTreeFunc(libX11, "XQueryTree");

    Window root, parent;
    Window* child;
    unsigned int numChildren;
    if (0 != XQueryTreeFunc(_display, w, &root,
            &parent, &child, &numChildren)) {
        for (unsigned int i = 0; i < numChildren; i++) {
            searchWindowHelper(child[i]);
        }
    }
}


extern "C" {
typedef Status (*XGetWindowAttributesFuncPtr)(Display *display, Window w,
        XWindowAttributes *window_attributes_return);

typedef Status (*XSendEventFuncPtr)(Display *display, Window w, Bool propagate,
        long event_mask, XEvent *event_send);

typedef int (*XRaiseWindowFuncPtr)(Display *display, Window w);
}

void ProcessReactivator::reactivateProcess() {

    DllFunction<XGetWindowAttributesFuncPtr> XGetWindowAttributesFunc(libX11,
            "XGetWindowAttributes");

    DllFunction<XSendEventFuncPtr> XSendEventFunc(libX11, "XSendEvent");

    DllFunction<XRaiseWindowFuncPtr> XRaiseWindowFunc(libX11, "XRaiseWindow");

    DllFunction<XInternAtomFuncPtr> XInternAtomFunc(libX11, "XInternAtom");

    for (std::list<Window>::const_iterator it = _result.begin();
            it != _result.end(); it++) {
        // try sending an event to activate window,
        // after that we can try to raise it.
        XEvent xev;
        Atom atom = XInternAtomFunc (
                _display, (char*)"_NET_ACTIVE_WINDOW", False);
        xev.xclient.type = ClientMessage;
        xev.xclient.serial = 0;
        xev.xclient.send_event = True;
        xev.xclient.display = _display;
        xev.xclient.window = *it;
        xev.xclient.message_type = atom;
        xev.xclient.format = 32;
        xev.xclient.data.l[0] = 2;
        xev.xclient.data.l[1] = 0;
        xev.xclient.data.l[2] = 0;
        xev.xclient.data.l[3] = 0;
        xev.xclient.data.l[4] = 0;
        XWindowAttributes attr;
        XGetWindowAttributesFunc(_display, *it, &attr);
        XSendEventFunc(_display, attr.root, False,
            SubstructureRedirectMask | SubstructureNotifyMask, &xev);
        XRaiseWindowFunc(_display, *it);
    }
}


#endif // LINUX
