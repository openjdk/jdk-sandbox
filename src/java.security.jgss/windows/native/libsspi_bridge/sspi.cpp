/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

#define UNICODE
#define _UNICODE

#include <windows.h>
#include <stdlib.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define GSS_DLL_FILE
#include "gssapi.h"

#define SECURITY_WIN32
#include <sspi.h>

#pragma comment(lib, "secur32.lib")

//#define DEBUG

#ifdef DEBUG
TCHAR _bb[256];
#define SEC_SUCCESS(Status) \
        ((Status) >= 0 ? TRUE: \
        (FormatMessage(FORMAT_MESSAGE_FROM_SYSTEM|FORMAT_MESSAGE_IGNORE_INSERTS, \
            0, ss, MAKELANGID(LANG_NEUTRAL, SUBLANG_DEFAULT), _bb, 256, 0), \
        printf("SECURITY_STATUS: (%lx) %ls\n", ss, _bb), \
        FALSE))
#define PP(fmt, ...) \
        fprintf(stdout, "SSPI (%ld): ", __LINE__); \
        fprintf(stdout, fmt, ##__VA_ARGS__); \
        fflush(stdout)
#else
#define SEC_SUCCESS(Status) ((Status) >= 0)
#define PP(dmt, ...)
#endif

char KRB5_OID[9] = {
        (char)0x2a, (char)0x86, (char)0x48, (char)0x86, (char)0xf7, (char)0x12,
        (char)0x01, (char)0x02, (char)0x02};
char SPNEGO_OID[6] = {
        (char)0x2b, (char)0x06, (char)0x01, (char)0x05, (char)0x05, (char)0x02};
char USER_NAME_OID[10] = {
        (char)0x2a, (char)0x86, (char)0x48, (char)0x86, (char)0xf7, (char)0x12,
        (char)0x01, (char)0x02, (char)0x01, (char)0x01};
char HOST_SERVICE_NAME_OID[10] = {
        (char)0x2a, (char)0x86, (char)0x48, (char)0x86, (char)0xf7, (char)0x12,
        (char)0x01, (char)0x02, (char)0x01, (char)0x04};

// gss_name_t is SecPkgCredentials_Names*
// gss_cred_id_t is CredHandle*
// gss_ctx_id_t is Context*

typedef struct {
    TCHAR PackageName[20];
    CredHandle* phCred;
    struct _SecHandle hCtxt;
    DWORD cbMaxMessage;
    SecPkgContext_Sizes SecPkgContextSizes;
} Context;

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

/* This section holds supporting functions that are not exported */

long
TimeStampToLong(TimeStamp *time)
{
    ULARGE_INTEGER *a, *b;
    FILETIME fnow;
    GetSystemTimeAsFileTime(&fnow);
    a = (ULARGE_INTEGER*)time;
    b = (ULARGE_INTEGER*)&fnow;
    PP("Difference %ld\n", (long)((a->QuadPart - b->QuadPart) / 10000000));
    return (long)((a->QuadPart - b->QuadPart) / 10000000);
}

void
FillContextAfterEstablished(Context *pc)
{
    QueryContextAttributes(
            &pc->hCtxt, SECPKG_ATTR_SIZES, &pc->SecPkgContextSizes);
}

Context*
NewContext(TCHAR* PackageName)
{
    SECURITY_STATUS ss;
    PSecPkgInfo pkgInfo;

    Context* out = (Context*)malloc(sizeof(Context));
    ss = QuerySecurityPackageInfo(PackageName, &pkgInfo);
    if (!SEC_SUCCESS(ss)) {
        return NULL;
    }
    out->phCred = NULL;
    out->cbMaxMessage = pkgInfo->cbMaxToken;
    PP("   QuerySecurityPackageInfo %ls goes %ld\n", PackageName, out->cbMaxMessage);
    wcscpy(out->PackageName, PackageName);
    FreeContextBuffer(pkgInfo);
    return out;
}

int
flagSspi2Gss(int fin)
{
	int fout = 0;
	if (fin & ISC_REQ_MUTUAL_AUTH) fout |= GSS_C_MUTUAL_FLAG;
	if (fin & ISC_REQ_CONFIDENTIALITY) fout |= GSS_C_CONF_FLAG;
	if (fin & ISC_REQ_DELEGATE) fout |= GSS_C_DELEG_FLAG;
	if (fin & ISC_REQ_INTEGRITY) fout |= GSS_C_INTEG_FLAG;
	if (fin & ISC_REQ_REPLAY_DETECT) fout |= GSS_C_REPLAY_FLAG;
	if (fin & ISC_REQ_SEQUENCE_DETECT) fout |= GSS_C_SEQUENCE_FLAG;
	return fout;
}

int
flagGss2Sspi(int fin)
{
	int fout = 0;
	if (fin & GSS_C_MUTUAL_FLAG) fout |= ISC_RET_MUTUAL_AUTH;
	if (fin & GSS_C_CONF_FLAG) fout |= ISC_RET_CONFIDENTIALITY;
	if (fin & GSS_C_DELEG_FLAG) fout |= ISC_RET_DELEGATE;
	if (fin & GSS_C_INTEG_FLAG) fout |= ISC_RET_INTEGRITY;
	if (fin & GSS_C_REPLAY_FLAG) fout |= ISC_RET_REPLAY_DETECT;
	if (fin & GSS_C_SEQUENCE_FLAG) fout |= ISC_RET_SEQUENCE_DETECT;
	return fout;
}

/* End support section */

/* This section holds exported functions that currently have no implementation */

__declspec(dllexport) OM_uint32
gss_release_name(OM_uint32 *minor_status,
                 gss_name_t *name)
{
    if (name != NULL) {
        SecPkgCredentials_Names* names = (SecPkgCredentials_Names*)name;
        if (names->sUserName != NULL) {
            delete[] names->sUserName;
        }
        delete names;
        *name = GSS_C_NO_NAME;
    }
    return GSS_S_COMPLETE;
}

__declspec(dllexport) OM_uint32
gss_import_name(OM_uint32 *minor_status,
                gss_buffer_t input_name_buffer,
                gss_OID input_name_type,
                gss_name_t *output_name)
{
    if (input_name_buffer == NULL || input_name_buffer->value == NULL
            || input_name_buffer->length == 0) {
        return GSS_S_BAD_NAME;
    }
    SecPkgCredentials_Names* names = new SecPkgCredentials_Names();
    if (names == NULL) {
        goto err;
    }
    int len = (int)input_name_buffer->length;
    names->sUserName = new SEC_WCHAR[len + 1];
    if (names->sUserName == NULL) {
        goto err;
    }
    if (MultiByteToWideChar(CP_ACP, 0, (LPSTR)input_name_buffer->value, len,
            names->sUserName, len) == 0) {
        goto err;
    }
    names->sUserName[len] = 0;
    if (input_name_type != NULL && input_name_type->length == 10
            && !memcmp(input_name_type->elements, HOST_SERVICE_NAME_OID, 10)) {
        for (int i = 0; i < len; i++) {
            if (names->sUserName[i] == '@') {
                names->sUserName[i] = '/';
                break;
            }
        }
    }
    *output_name = (gss_name_t) names;
    return GSS_S_COMPLETE;
err:
    if (names != NULL && names->sUserName != NULL) {
        delete[] names->sUserName;
    }
    if (names != NULL) {
        delete names;
    }
    return GSS_S_FAILURE;
}

__declspec(dllexport) OM_uint32
gss_compare_name(OM_uint32 *minor_status,
                 gss_name_t name1,
                 gss_name_t name2,
                 int *name_equal)
{
    if (name1 == NULL || name2 == NULL) {
        *name_equal = 0;
        return GSS_S_BAD_NAME;
    }

    SecPkgCredentials_Names* names1 = (SecPkgCredentials_Names*)name1;
    SecPkgCredentials_Names* names2 = (SecPkgCredentials_Names*)name2;
    if (lstrcmp(names1->sUserName, names2->sUserName)) {
        *name_equal = 0;
    } else {
        *name_equal = 1;
    }
    return GSS_S_COMPLETE;
}

__declspec(dllexport) OM_uint32
gss_canonicalize_name(OM_uint32 *minor_status,
                      gss_name_t input_name,
                      gss_OID mech_type,
                      gss_name_t *output_name)
{
    SecPkgCredentials_Names* names1 = (SecPkgCredentials_Names*)input_name;
    SecPkgCredentials_Names* names2 = new SecPkgCredentials_Names();
    names2->sUserName = new SEC_WCHAR[lstrlen(names1->sUserName) + 1];
    lstrcpy(names2->sUserName, names1->sUserName);
    *output_name = (gss_name_t)names2;
    return GSS_S_COMPLETE;
}

__declspec(dllexport) OM_uint32
gss_export_name(OM_uint32 *minor_status,
                gss_name_t input_name,
                gss_buffer_t exported_name)
{
    SecPkgCredentials_Names* names = (SecPkgCredentials_Names*)input_name;
    int len = (int)wcslen(names->sUserName);
    char* buffer = new char[len+1];
    WideCharToMultiByte(CP_ACP, 0, names->sUserName, len, buffer, len, NULL, NULL);
    buffer[len] = 0;
    exported_name->length = len+1;
    exported_name->value = buffer;
    return GSS_S_FAILURE;
}

__declspec(dllexport) OM_uint32
gss_display_name(OM_uint32 *minor_status,
                 gss_name_t input_name,
                 gss_buffer_t output_name_buffer,
                 gss_OID *output_name_type)
{
    SecPkgCredentials_Names* names = (SecPkgCredentials_Names*)input_name;
    int len = (int)wcslen(names->sUserName);
    char* buffer = new char[len+1];
    WideCharToMultiByte(CP_ACP, 0, names->sUserName, len, buffer, len, NULL, NULL);
    buffer[len] = 0;
    output_name_buffer->length = len+1;
    output_name_buffer->value = buffer;
    PP("Name found: %ls\n", names->sUserName);
    PP("%d [%s]", len, buffer);
    if (output_name_type != NULL) {
        gss_OID_desc* oid = new gss_OID_desc();
        oid->length = (OM_uint32)strlen(USER_NAME_OID);
        oid->elements = strdup(USER_NAME_OID);
        *output_name_type = oid;
    }
    return GSS_S_COMPLETE;
}

__declspec(dllexport) OM_uint32
gss_acquire_cred(OM_uint32 *minor_status,
                 gss_name_t desired_name,
                 OM_uint32 time_req,
                 gss_OID_set desired_mech,
                 gss_cred_usage_t cred_usage,
                 gss_cred_id_t *output_cred_handle,
                 gss_OID_set *actual_mechs,
                 OM_uint32 *time_rec)
{
    if (desired_name != NULL) {
        return GSS_S_FAILURE; // Only support default cred
    }
    SECURITY_STATUS ss;
    CredHandle* cred = new CredHandle();
    TimeStamp ts;
	cred_usage = 0;
    PP("AcquireCredentialsHandle with %d\n", cred_usage);
    ss = AcquireCredentialsHandle(
            NULL,
            L"Kerberos",
            cred_usage == 0 ? SECPKG_CRED_BOTH :
                (cred_usage == 1 ? SECPKG_CRED_OUTBOUND : SECPKG_CRED_INBOUND),
            NULL,
            NULL,
            NULL,
            NULL,
            cred,
            &ts);

    actual_mechs = &desired_mech;
    *output_cred_handle = (void*)cred;
    if (time_rec != NULL) {
        *time_rec = TimeStampToLong(&ts);
    }

    return GSS_S_COMPLETE;
}

__declspec(dllexport) OM_uint32
gss_release_cred(OM_uint32 *minor_status,
                 gss_cred_id_t *cred_handle)
{
    if (cred_handle && *cred_handle) {
        FreeCredentialsHandle((CredHandle*)*cred_handle);
        *cred_handle = GSS_C_NO_CREDENTIAL;
    }
    return GSS_S_COMPLETE;
}

__declspec(dllexport) OM_uint32
gss_inquire_cred(OM_uint32 *minor_status,
                 gss_cred_id_t cred_handle,
                 gss_name_t *name,
                 OM_uint32 *lifetime,
                 gss_cred_usage_t *cred_usage,
                 gss_OID_set *mechanisms)
{
    CredHandle* cred = (CredHandle*)cred_handle;
    SECURITY_STATUS ss;
    if (name) {
        SecPkgCredentials_Names* names = new SecPkgCredentials_Names();
        ss = QueryCredentialsAttributes(cred, SECPKG_CRED_ATTR_NAMES, names);
        *name = (gss_name_t) names;
    }
    // Others inquiries not supported yet
    return GSS_S_COMPLETE;
}

__declspec(dllexport) OM_uint32
gss_import_sec_context(OM_uint32 *minor_status,
                       gss_buffer_t interprocess_token,
                       gss_ctx_id_t *context_handle)
{
    return GSS_S_FAILURE;
}

__declspec(dllexport) OM_uint32
gss_init_sec_context(OM_uint32 *minor_status,
                     gss_cred_id_t initiator_cred_handle,
                     gss_ctx_id_t *context_handle,
                     gss_name_t target_name,
                     gss_OID mech_type,
                     OM_uint32 req_flags,
                     OM_uint32 time_req,
                     gss_channel_bindings_t input_chan_bindings,
                     gss_buffer_t input_token,
                     gss_OID *actual_mech_type,
                     gss_buffer_t output_token,
                     OM_uint32 *ret_flags,
                     OM_uint32 *time_rec)
{
    SECURITY_STATUS ss;
    TimeStamp Lifetime;
    SecBufferDesc InBuffDesc;
    SecBuffer InSecBuff;
    SecBufferDesc OutBuffDesc;
    SecBuffer OutSecBuff;

    Context* pc;
    if (input_token->length == 0) {
        pc = NewContext(L"Kerberos");
        pc->phCred = (CredHandle*)initiator_cred_handle;
        *context_handle = (gss_ctx_id_t) pc;
    } else {
        pc = (Context*)*context_handle;
    }

    output_token->length = pc->cbMaxMessage;
    output_token->value = new char[pc->cbMaxMessage];

    DWORD outFlag;    
    TCHAR outName[100];

    OM_uint32 minor;
    gss_buffer_desc tn;
    gss_display_name(&minor, target_name, &tn, NULL);
    MultiByteToWideChar(CP_ACP, 0, (LPCCH)tn.value, (int)tn.length,
            outName, (int)tn.length);
    outName[tn.length] = 0;

    BOOL pfDone;
    int flag = flagGss2Sspi(req_flags);

    OutBuffDesc.ulVersion = SECBUFFER_VERSION;
    OutBuffDesc.cBuffers = 1;
    OutBuffDesc.pBuffers = &OutSecBuff;

    OutSecBuff.cbBuffer = (ULONG)output_token->length;
    OutSecBuff.BufferType = SECBUFFER_TOKEN;
    OutSecBuff.pvBuffer = output_token->value;

    if (input_token->value) {
        InBuffDesc.ulVersion = SECBUFFER_VERSION;
        InBuffDesc.cBuffers = 1;
        InBuffDesc.pBuffers = &InSecBuff;

        InSecBuff.BufferType = SECBUFFER_TOKEN;
        InSecBuff.cbBuffer = (ULONG)input_token->length;
        InSecBuff.pvBuffer = input_token->value;
    } else {
        if (!pc->phCred) {
            PP("No credentials provided, acquire automatically");
            ss = AcquireCredentialsHandle(
                    NULL,
                    pc->PackageName,
                    SECPKG_CRED_OUTBOUND,
                    NULL,
                    NULL,
                    NULL,
                    NULL,
                    pc->phCred,
                    &Lifetime);
            PP("end");
            if (!(SEC_SUCCESS(ss))) {
                PP("Failed");
                return GSS_S_FAILURE;
            }
        } else {
            PP("Credentials OK");
        }
    }
    ss = InitializeSecurityContext(
            pc->phCred,
            input_token->value ? &pc->hCtxt : NULL,
            outName,
            flag,
            0,
            SECURITY_NATIVE_DREP,
            input_token->value ? &InBuffDesc : NULL,
            0,
            &pc->hCtxt,
            &OutBuffDesc,
            &outFlag,
            &Lifetime);

	if (!SEC_SUCCESS(ss)) {
		return GSS_S_FAILURE;
	}

    if ((SEC_I_COMPLETE_NEEDED == ss)
            || (SEC_I_COMPLETE_AND_CONTINUE == ss)) {
        ss = CompleteAuthToken(&pc->hCtxt, &OutBuffDesc);
        if (!SEC_SUCCESS(ss)) {
            return GSS_S_FAILURE;
        }
    }

    output_token->length =  OutSecBuff.cbBuffer;

    pfDone = !((SEC_I_CONTINUE_NEEDED == ss) ||
                (SEC_I_COMPLETE_AND_CONTINUE == ss));

    if (ss == SEC_E_OK) FillContextAfterEstablished(pc);

	outFlag = flagSspi2Gss(outFlag);

    *ret_flags = (OM_uint32)outFlag;
    if (ss == SEC_I_CONTINUE_NEEDED) {
        return GSS_S_CONTINUE_NEEDED;
    } else {
        *ret_flags |= GSS_C_PROT_READY_FLAG;
        return GSS_S_COMPLETE;
    }
}

__declspec(dllexport) OM_uint32
gss_accept_sec_context(OM_uint32 *minor_status,
                       gss_ctx_id_t *context_handle,
                       gss_cred_id_t acceptor_cred_handle,
                       gss_buffer_t input_token,
                       gss_channel_bindings_t input_chan_bindings,
                       gss_name_t *src_name,
                       gss_OID *mech_type,
                       gss_buffer_t output_token,
                       OM_uint32 *ret_flags,
                       OM_uint32 *time_rec,
                       gss_cred_id_t *delegated_cred_handle)
{
    return GSS_S_FAILURE;
}

__declspec(dllexport) OM_uint32
gss_inquire_context(OM_uint32 *minor_status,
                    gss_ctx_id_t context_handle,
                    gss_name_t *src_name,
                    gss_name_t *targ_name,
                    OM_uint32 *lifetime_rec,
                    gss_OID *mech_type,
                    OM_uint32 *ctx_flags,
                    int *locally_initiated,
                    int *open)
{
    Context* pc = (Context*) context_handle;
    return GSS_S_FAILURE;
}

__declspec(dllexport) OM_uint32
gss_delete_sec_context(OM_uint32 *minor_status,
                       gss_ctx_id_t *context_handle,
                       gss_buffer_t output_token)
{
    return GSS_S_FAILURE;
}

__declspec(dllexport) OM_uint32
gss_context_time(OM_uint32 *minor_status,
                 gss_ctx_id_t context_handle,
                 OM_uint32 *time_rec)
{
    Context* pc = (Context*) context_handle;
    return GSS_S_FAILURE;
}

__declspec(dllexport) OM_uint32
gss_wrap_size_limit(OM_uint32 *minor_status,
                    gss_ctx_id_t context_handle,
                    int conf_req_flag,
                    gss_qop_t qop_req,
                    OM_uint32 req_output_size,
                    OM_uint32 *max_input_size)
{
    Context* pc = (Context*) context_handle;
    *max_input_size = pc->cbMaxMessage;
    return GSS_S_COMPLETE;
}

__declspec(dllexport) OM_uint32
gss_export_sec_context(OM_uint32 *minor_status,
                       gss_ctx_id_t *context_handle,
                       gss_buffer_t interprocess_token)
{
    return GSS_S_FAILURE;
}

__declspec(dllexport) OM_uint32
gss_get_mic(OM_uint32 *minor_status,
            gss_ctx_id_t context_handle,
            gss_qop_t qop_req,
            gss_buffer_t message_buffer,
            gss_buffer_t msg_token)
{
    Context* pc = (Context*) context_handle;

    SECURITY_STATUS ss;
    SecBufferDesc BuffDesc;
    SecBuffer SecBuff[2];

    BuffDesc.cBuffers = 2;
    BuffDesc.pBuffers = SecBuff;
    BuffDesc.ulVersion = SECBUFFER_VERSION;

    SecBuff[0].BufferType = SECBUFFER_DATA;
    SecBuff[0].cbBuffer = (ULONG)message_buffer->length;
    SecBuff[0].pvBuffer = message_buffer->value;

    SecBuff[1].BufferType = SECBUFFER_TOKEN;
    SecBuff[1].cbBuffer = pc->SecPkgContextSizes.cbMaxSignature;
    SecBuff[1].pvBuffer = msg_token->value = malloc(SecBuff[1].cbBuffer);

    ss = MakeSignature(&pc->hCtxt, 0, &BuffDesc, 0);

    if (!SEC_SUCCESS(ss)) {
        free(SecBuff[1].pvBuffer);
        return GSS_S_FAILURE;
    }

    msg_token->length = SecBuff[1].cbBuffer;
    return GSS_S_COMPLETE;
}

__declspec(dllexport) OM_uint32
gss_verify_mic(OM_uint32 *minor_status,
               gss_ctx_id_t context_handle,
               gss_buffer_t message_buffer,
               gss_buffer_t token_buffer,
               gss_qop_t *qop_state)
{
    Context* pc = (Context*) context_handle;

    SECURITY_STATUS ss;
    SecBufferDesc BuffDesc;
    SecBuffer SecBuff[2];
    ULONG qop;

    BuffDesc.ulVersion = 0;
    BuffDesc.cBuffers = 2;
    BuffDesc.pBuffers = SecBuff;

    SecBuff[0].BufferType = SECBUFFER_TOKEN;
    SecBuff[0].cbBuffer = (ULONG)token_buffer->length;
    SecBuff[0].pvBuffer = token_buffer->value;

    SecBuff[1].BufferType = SECBUFFER_DATA;
    SecBuff[1].cbBuffer = (ULONG)message_buffer->length;
    SecBuff[1].pvBuffer = message_buffer->value;

    ss = VerifySignature(&pc->hCtxt, &BuffDesc, 0, &qop);
    *qop_state = qop;

    if (ss == SEC_E_OK) {
        return GSS_S_COMPLETE;
    } else if (ss == SEC_E_OUT_OF_SEQUENCE) {
        return GSS_S_UNSEQ_TOKEN;
    } else {
        return GSS_S_BAD_SIG;
    }
}

__declspec(dllexport) OM_uint32
gss_wrap(OM_uint32 *minor_status,
         gss_ctx_id_t context_handle,
         int conf_req_flag,
         gss_qop_t qop_req,
         gss_buffer_t input_message_buffer,
         int *conf_state,
         gss_buffer_t output_message_buffer)
{
    Context* pc = (Context*) context_handle;

    SECURITY_STATUS ss;
    SecBufferDesc BuffDesc;
    SecBuffer SecBuff[3];

    BuffDesc.ulVersion = SECBUFFER_VERSION;
    BuffDesc.cBuffers = 3;
    BuffDesc.pBuffers = SecBuff;

    SecBuff[0].BufferType = SECBUFFER_TOKEN;
    SecBuff[0].cbBuffer = pc->SecPkgContextSizes.cbSecurityTrailer;
    output_message_buffer->value = SecBuff[0].pvBuffer = malloc(
            pc->SecPkgContextSizes.cbSecurityTrailer
                    + input_message_buffer->length
                    + pc->SecPkgContextSizes.cbBlockSize);;

    SecBuff[1].BufferType = SECBUFFER_DATA;
    SecBuff[1].cbBuffer = (ULONG)input_message_buffer->length;
    SecBuff[1].pvBuffer = malloc(SecBuff[1].cbBuffer);
    memcpy(SecBuff[1].pvBuffer, input_message_buffer->value,
            input_message_buffer->length);

    SecBuff[2].BufferType = SECBUFFER_PADDING;
    SecBuff[2].cbBuffer = pc->SecPkgContextSizes.cbBlockSize;
    SecBuff[2].pvBuffer = malloc(SecBuff[2].cbBuffer);

    ss = EncryptMessage(&pc->hCtxt, conf_req_flag ? 0 : SECQOP_WRAP_NO_ENCRYPT,
            &BuffDesc, 0);
    *conf_state = conf_req_flag;

    if (!SEC_SUCCESS(ss)) {
        free(SecBuff[0].pvBuffer);
        free(SecBuff[1].pvBuffer);
        free(SecBuff[2].pvBuffer);
        return GSS_S_FAILURE;
    }

    memcpy((PBYTE)SecBuff[0].pvBuffer + SecBuff[0].cbBuffer, SecBuff[1].pvBuffer,
            SecBuff[1].cbBuffer);
    memcpy((PBYTE)SecBuff[0].pvBuffer + SecBuff[0].cbBuffer + SecBuff[1].cbBuffer,
            SecBuff[2].pvBuffer, SecBuff[2].cbBuffer);

    output_message_buffer->length = SecBuff[1].cbBuffer + SecBuff[0].cbBuffer
            + SecBuff[2].cbBuffer;
    free(SecBuff[1].pvBuffer);
    free(SecBuff[2].pvBuffer);

    return GSS_S_COMPLETE;
}

__declspec(dllexport) OM_uint32
gss_unwrap(OM_uint32 *minor_status,
           gss_ctx_id_t context_handle,
           gss_buffer_t input_message_buffer,
           gss_buffer_t output_message_buffer,
           int *conf_state,
           gss_qop_t *qop_state)
{
    Context* pc = (Context*) context_handle;

    SECURITY_STATUS ss;
    SecBufferDesc BuffDesc;
    SecBuffer SecBuff[2];
    ULONG ulQop = 0;

    BuffDesc.cBuffers = 2;
    BuffDesc.pBuffers = SecBuff;
    BuffDesc.ulVersion = SECBUFFER_VERSION;

    SecBuff[0].BufferType = SECBUFFER_STREAM;
    SecBuff[0].cbBuffer = (ULONG)input_message_buffer->length;
    output_message_buffer->value = SecBuff[0].pvBuffer
            = malloc(input_message_buffer->length);
    memcpy(SecBuff[0].pvBuffer, input_message_buffer->value,
            input_message_buffer->length);

    SecBuff[1].BufferType = SECBUFFER_DATA;
    SecBuff[1].cbBuffer = 0;
    SecBuff[1].pvBuffer = NULL;

    ss = DecryptMessage(&pc->hCtxt, &BuffDesc, 0, &ulQop);
    if (!SEC_SUCCESS(ss)) {
        free(SecBuff[0].pvBuffer);
        return GSS_S_FAILURE;
    }
    output_message_buffer->length = SecBuff[1].cbBuffer;
    output_message_buffer->value = SecBuff[1].pvBuffer;
    *conf_state = ulQop == SECQOP_WRAP_NO_ENCRYPT ? 0 : 1;
    return GSS_S_COMPLETE;
}

__declspec(dllexport) OM_uint32
gss_indicate_mechs(OM_uint32 *minor_status,
                   gss_OID_set *mech_set)
{
    gss_OID_set_desc *copy;
    OM_uint32 minor = 0;
    OM_uint32 major = GSS_S_COMPLETE;
    int n = 0;
    int i = 0;
    BOOLEAN hasSpnego = false, hasKerberos = false;

    ULONG ccPackages;
    PSecPkgInfo packages;
    EnumerateSecurityPackages(&ccPackages, &packages);
    PP("EnumerateSecurityPackages returns %ld\n", ccPackages);
    // TODO: only return Kerberos, so no need to check input later
    PSecPkgInfo pkgInfo;
    SECURITY_STATUS ss = QuerySecurityPackageInfo(L"Negotiate", &pkgInfo);
    if (ss == SEC_E_OK) {
        // n++;
        // hasSpnego = true;
    }
    ss = QuerySecurityPackageInfo(L"Kerberos", &pkgInfo);
    if (ss == SEC_E_OK) {
        n++;
        hasKerberos = true;
    }

    if ((copy = new gss_OID_set_desc[1]) == NULL) {
        major = GSS_S_FAILURE;
        goto done;
    }

    if ((copy->elements = new gss_OID_desc[n]) == NULL) {
        major = GSS_S_FAILURE;
        goto done;
    }

    if (hasKerberos) {
        gss_OID_desc *out = &copy->elements[i];
        if ((out->elements = new char[sizeof(KRB5_OID)]) == NULL) {
            major = GSS_S_FAILURE;
            goto done;
        }
        (void) memcpy(out->elements, KRB5_OID, sizeof(KRB5_OID));
        out->length = sizeof(KRB5_OID);
        i++;
    }    
    if (hasSpnego) {
        gss_OID_desc *out = &copy->elements[i];
        char in[6] = { 0x2B, 0x06, 0x01, 0x05, 0x05, 0x02 };
        if ((out->elements = new char[sizeof(in)]) == NULL) {
            major = GSS_S_FAILURE;
            goto done;
        }
        (void) memcpy(out->elements, in, sizeof(in));
        out->length = sizeof(in);
        i++;
    }    
    copy->count = i;

    *mech_set = copy;
done:
    if (major != GSS_S_COMPLETE) {
        // (void) generic_gss_release_oid_set(&minor, &copy);
    }

    return (major);
}

__declspec(dllexport) OM_uint32
gss_inquire_names_for_mech(OM_uint32 *minor_status,
                           const gss_OID mechanism,
                           gss_OID_set *name_types)
{
    return GSS_S_FAILURE;
}

__declspec(dllexport) OM_uint32
gss_add_oid_set_member(OM_uint32 *minor_status,
                       gss_OID member_oid,
                       gss_OID_set *oid_set)
{
    return GSS_S_FAILURE;
}

__declspec(dllexport) OM_uint32
gss_display_status(OM_uint32 *minor_status,
                   OM_uint32 status_value,
                   int status_type,
                   gss_OID mech_type,
                   OM_uint32 *message_context,
                   gss_buffer_t status_string)
{
    return GSS_S_FAILURE;
}

__declspec(dllexport) OM_uint32
gss_create_empty_oid_set(OM_uint32 *minor_status,
                         gss_OID_set *oid_set)
{
    return GSS_S_FAILURE;
}

__declspec(dllexport) OM_uint32
gss_release_oid_set(OM_uint32 *minor_status,
                    gss_OID_set *set)
{
    return GSS_S_FAILURE;
}

__declspec(dllexport) OM_uint32
gss_release_buffer(OM_uint32 *minor_status,
                   gss_buffer_t buffer)
{
    return GSS_S_FAILURE;
}

/* End implemented section */

#ifdef __cplusplus
}
#endif
