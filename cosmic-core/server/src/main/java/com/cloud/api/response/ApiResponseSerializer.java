package com.cloud.api.response;

import com.cloud.acl.RoleType;
import com.cloud.api.ApiConstants;
import com.cloud.api.ApiDBUtils;
import com.cloud.api.ApiResponseGsonHelper;
import com.cloud.api.ApiServer;
import com.cloud.api.BaseCmd;
import com.cloud.api.ResponseObject;
import com.cloud.context.CallContext;
import com.cloud.legacymodel.exceptions.CloudRuntimeException;
import com.cloud.legacymodel.exceptions.ExceptionProxyObject;
import com.cloud.legacymodel.user.Account;
import com.cloud.serializer.Param;
import com.cloud.utils.HttpUtils;
import com.cloud.utils.encoding.URLEncoder;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ApiResponseSerializer {
    private static final Logger s_logger = LoggerFactory.getLogger(ApiResponseSerializer.class.getName());
    private static final Pattern s_unicodeEscapePattern = Pattern.compile("\\\\u([0-9A-Fa-f]{4})");

    public static String toSerializedString(final ResponseObject result, final String responseType) {
        s_logger.trace("===Serializing Response===");
        if (HttpUtils.RESPONSE_TYPE_JSON.equalsIgnoreCase(responseType)) {
            return toJSONSerializedString(result, new StringBuilder());
        } else {
            return toXMLSerializedString(result, new StringBuilder());
        }
    }

    public static String toSerializedStringWithSecureLogs(final ResponseObject result, final String responseType, final StringBuilder log) {
        s_logger.trace("===Serializing Response===");
        if (HttpUtils.RESPONSE_TYPE_JSON.equalsIgnoreCase(responseType)) {
            return toJSONSerializedString(result, log);
        } else {
            return toXMLSerializedString(result, log);
        }
    }

    public static String unescape(final String escaped) {
        String str = escaped;
        final Matcher matcher = s_unicodeEscapePattern.matcher(str);
        while (matcher.find()) {
            str = str.replaceAll("\\" + matcher.group(0), Character.toString((char) Integer.parseInt(matcher.group(1), 16)));
        }
        return str;
    }

    public static String toJSONSerializedString(final ResponseObject result, final StringBuilder log) {
        if (result != null && log != null) {
            final Gson responseBuilder = ApiResponseGsonHelper.getBuilder().excludeFieldsWithModifiers(Modifier.TRANSIENT).create();
            final Gson logBuilder = ApiResponseGsonHelper.getLogBuilder().excludeFieldsWithModifiers(Modifier.TRANSIENT).create();

            final StringBuilder sb = new StringBuilder();

            sb.append("{\"").append(result.getResponseName()).append("\":");
            log.append("{\"").append(result.getResponseName()).append("\":");
            if (result instanceof ListResponse) {
                final List<? extends ResponseObject> responses = ((ListResponse) result).getResponses();
                final Integer count = ((ListResponse) result).getCount();
                final boolean nonZeroCount = (count != null && count.longValue() != 0);
                if (nonZeroCount) {
                    sb.append("{\"").append(ApiConstants.COUNT).append("\":").append(count);
                    log.append("{\"").append(ApiConstants.COUNT).append("\":").append(count);
                }

                if ((responses != null) && !responses.isEmpty()) {
                    String jsonStr = responseBuilder.toJson(responses.get(0));
                    jsonStr = unescape(jsonStr);
                    String logStr = logBuilder.toJson(responses.get(0));
                    logStr = unescape(logStr);

                    if (nonZeroCount) {
                        sb.append(",\"").append(responses.get(0).getObjectName()).append("\":[").append(jsonStr);
                        log.append(",\"").append(responses.get(0).getObjectName()).append("\":[").append(logStr);
                    }

                    for (int i = 1; i < ((ListResponse) result).getResponses().size(); i++) {
                        jsonStr = responseBuilder.toJson(responses.get(i));
                        jsonStr = unescape(jsonStr);
                        logStr = logBuilder.toJson(responses.get(i));
                        logStr = unescape(logStr);
                        sb.append(",").append(jsonStr);
                        log.append(",").append(logStr);
                    }
                    sb.append("]}");
                    log.append("]}");
                } else {
                    if (!nonZeroCount) {
                        sb.append("{");
                        log.append("{");
                    }

                    sb.append("}");
                    log.append("}");
                }
            } else if (result instanceof SuccessResponse) {
                sb.append("{\"success\":\"").append(((SuccessResponse) result).getSuccess()).append("\"}");
                log.append("{\"success\":\"").append(((SuccessResponse) result).getSuccess()).append("\"}");
            } else if (result instanceof ExceptionResponse) {
                String jsonErrorText = responseBuilder.toJson(result);
                jsonErrorText = unescape(jsonErrorText);
                sb.append(jsonErrorText);
                log.append(jsonErrorText);
            } else {
                String jsonStr = responseBuilder.toJson(result);
                if (jsonStr != null && !jsonStr.isEmpty()) {
                    jsonStr = unescape(jsonStr);
                    if (result instanceof AsyncJobResponse || result instanceof CreateCmdResponse || result instanceof AuthenticationCmdResponse) {
                        sb.append(jsonStr);
                    } else {
                        sb.append("{\"").append(result.getObjectName()).append("\":").append(jsonStr).append("}");
                    }
                } else {
                    sb.append("{}");
                }
                String logStr = logBuilder.toJson(result);
                if (logStr != null && !logStr.isEmpty()) {
                    logStr = unescape(logStr);
                    if (result instanceof AsyncJobResponse || result instanceof CreateCmdResponse || result instanceof AuthenticationCmdResponse) {
                        log.append(logStr);
                    } else {
                        log.append("{\"").append(result.getObjectName()).append("\":").append(logStr).append("}");
                    }
                } else {
                    log.append("{}");
                }
            }
            sb.append("}");
            log.append("}");
            return sb.toString();
        }
        return null;
    }

    private static String toXMLSerializedString(final ResponseObject result, final StringBuilder log) {
        if (result != null && log != null) {
            final StringBuilder sb = new StringBuilder();
            sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
            sb.append("<").append(result.getResponseName()).append(" cosmic-version=\"").append(ApiDBUtils.getVersion()).append("\">");
            log.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
            log.append("<").append(result.getResponseName()).append(" cosmic-version=\"").append(ApiDBUtils.getVersion()).append("\">");

            if (result instanceof ListResponse) {
                final Integer count = ((ListResponse) result).getCount();

                if (count != null && count != 0) {
                    sb.append("<").append(ApiConstants.COUNT).append(">").append(((ListResponse) result).getCount()).append("</").append(ApiConstants.COUNT).append(">");
                    log.append("<").append(ApiConstants.COUNT).append(">").append(((ListResponse) result).getCount()).append("</").append(ApiConstants.COUNT).append(">");
                }
                final List<? extends ResponseObject> responses = ((ListResponse) result).getResponses();
                if ((responses != null) && !responses.isEmpty()) {
                    for (final ResponseObject obj : responses) {
                        serializeResponseObjXML(sb, log, obj);
                    }
                }
            } else {
                if (result instanceof CreateCmdResponse || result instanceof AsyncJobResponse || result instanceof AuthenticationCmdResponse) {
                    serializeResponseObjFieldsXML(sb, log, result);
                } else {
                    serializeResponseObjXML(sb, log, result);
                }
            }

            sb.append("</").append(result.getResponseName()).append(">");
            log.append("</").append(result.getResponseName()).append(">");
            return sb.toString();
        }
        return null;
    }

    private static void serializeResponseObjXML(final StringBuilder sb, final StringBuilder log, final ResponseObject obj) {
        if (!(obj instanceof SuccessResponse) && !(obj instanceof ExceptionResponse)) {
            sb.append("<").append(obj.getObjectName()).append(">");
            log.append("<").append(obj.getObjectName()).append(">");
        }
        serializeResponseObjFieldsXML(sb, log, obj);
        if (!(obj instanceof SuccessResponse) && !(obj instanceof ExceptionResponse)) {
            sb.append("</").append(obj.getObjectName()).append(">");
            log.append("</").append(obj.getObjectName()).append(">");
        }
    }

    private static Field[] getFlattenFields(final Class<?> clz) {
        final List<Field> fields = new ArrayList<>();
        fields.addAll(Arrays.asList(clz.getDeclaredFields()));
        if (clz.getSuperclass() != null) {
            fields.addAll(Arrays.asList(getFlattenFields(clz.getSuperclass())));
        }
        return fields.toArray(new Field[]{});
    }

    private static void serializeResponseObjFieldsXML(final StringBuilder sb, final StringBuilder log, final ResponseObject obj) {
        boolean isAsync = false;
        if (obj instanceof AsyncJobResponse) {
            isAsync = true;
        }

        final Field[] fields = getFlattenFields(obj.getClass());
        for (final Field field : fields) {
            if ((field.getModifiers() & Modifier.TRANSIENT) != 0) {
                continue; // skip transient fields
            }

            final SerializedName serializedName = field.getAnnotation(SerializedName.class);
            if (serializedName == null) {
                continue; // skip fields w/o serialized name
            }

            boolean logField = true;
            final Param param = field.getAnnotation(Param.class);
            if (param != null) {
                final RoleType[] allowedRoles = param.authorized();
                if (allowedRoles.length > 0) {
                    boolean permittedParameter = false;
                    final Account caller = CallContext.current().getCallingAccount();
                    for (final RoleType allowedRole : allowedRoles) {
                        if (allowedRole.getValue() == caller.getType()) {
                            permittedParameter = true;
                            break;
                        }
                    }
                    if (!permittedParameter) {
                        s_logger.trace("Ignoring parameter " + param.name() + " as the caller is not authorized to see it");
                        continue;
                    }
                }
                if (param.isSensitive()) {
                    logField = false;
                }
            }

            field.setAccessible(true);
            final Object fieldValue;
            try {
                fieldValue = field.get(obj);
            } catch (final IllegalArgumentException e) {
                throw new CloudRuntimeException("how illegal is it?", e);
            } catch (final IllegalAccessException e) {
                throw new CloudRuntimeException("come on...we set accessible already", e);
            }
            if (fieldValue != null) {
                if (fieldValue instanceof ResponseObject) {
                    final ResponseObject subObj = (ResponseObject) fieldValue;
                    if (isAsync) {
                        sb.append("<jobresult>");
                        log.append("<jobresult>");
                    }
                    serializeResponseObjXML(sb, log, subObj);
                    if (isAsync) {
                        sb.append("</jobresult>");
                        log.append("</jobresult>");
                    }
                } else if (fieldValue instanceof Collection<?>) {
                    final Collection<?> subResponseList = (Collection<?>) fieldValue;
                    boolean usedUuidList = false;
                    for (final Object value : subResponseList) {
                        if (value instanceof ResponseObject) {
                            final ResponseObject subObj = (ResponseObject) value;
                            if (serializedName != null) {
                                subObj.setObjectName(serializedName.value());
                            }
                            serializeResponseObjXML(sb, log, subObj);
                        } else if (value instanceof ExceptionProxyObject) {
                            // Only exception reponses carry a list of
                            // ExceptionProxyObject objects.
                            final ExceptionProxyObject idProxy = (ExceptionProxyObject) value;
                            // If this is the first IdentityProxy field
                            // encountered, put in a uuidList tag.
                            if (!usedUuidList) {
                                sb.append("<" + serializedName.value() + ">");
                                log.append("<" + serializedName.value() + ">");
                                usedUuidList = true;
                            }
                            sb.append("<" + "uuid" + ">" + idProxy.getUuid() + "</" + "uuid" + ">");
                            log.append("<" + "uuid" + ">" + idProxy.getUuid() + "</" + "uuid" + ">");
                            // Append the new descriptive property also.
                            final String idFieldName = idProxy.getDescription();
                            if (idFieldName != null) {
                                sb.append("<" + "uuidProperty" + ">" + idFieldName + "</" + "uuidProperty" + ">");
                                log.append("<" + "uuidProperty" + ">" + idFieldName + "</" + "uuidProperty" + ">");
                            }
                        } else if (value instanceof String) {
                            sb.append("<").append(serializedName.value()).append(">").append(value).append("</").append(serializedName.value()).append(">");
                            if (logField) {
                                log.append("<").append(serializedName.value()).append(">").append(value).append("</").append(serializedName.value()).append(">");
                            }
                        }
                    }
                    if (usedUuidList) {
                        // close the uuidList.
                        sb.append("</").append(serializedName.value()).append(">");
                        log.append("</").append(serializedName.value()).append(">");
                    }
                } else if (fieldValue instanceof Date) {
                    sb.append("<").append(serializedName.value()).append(">").append(BaseCmd.getDateString((Date) fieldValue)).append("</").append(serializedName.value()).append
                            (">");
                    log.append("<").append(serializedName.value()).append(">").append(BaseCmd.getDateString((Date) fieldValue)).append("</").append(serializedName.value())
                       .append(">");
                } else {
                    String resultString = escapeSpecialXmlChars(fieldValue.toString());
                    if (!(obj instanceof ExceptionResponse)) {
                        resultString = encodeParam(resultString);
                    }

                    sb.append("<").append(serializedName.value()).append(">").append(resultString).append("</").append(serializedName.value()).append(">");
                    if (logField) {
                        log.append("<").append(serializedName.value()).append(">").append(resultString).append("</").append(serializedName.value()).append(">");
                    }
                }
            }
        }
    }

    private static String escapeSpecialXmlChars(final String originalString) {
        final char[] origChars = originalString.toCharArray();
        final StringBuilder resultString = new StringBuilder();

        for (final char singleChar : origChars) {
            if (singleChar == '"') {
                resultString.append("&quot;");
            } else if (singleChar == '\'') {
                resultString.append("&apos;");
            } else if (singleChar == '<') {
                resultString.append("&lt;");
            } else if (singleChar == '>') {
                resultString.append("&gt;");
            } else if (singleChar == '&') {
                resultString.append("&amp;");
            } else {
                resultString.append(singleChar);
            }
        }

        return resultString.toString();
    }

    private static String encodeParam(final String value) {
        if (!ApiServer.isEncodeApiResponse()) {
            return value;
        }
        try {
            return new URLEncoder().encode(value).replaceAll("\\+", "%20");
        } catch (final Exception e) {
            s_logger.warn("Unable to encode: " + value, e);
        }
        return value;
    }
}
