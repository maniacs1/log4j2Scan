package burp.Bootstrap;

import burp.*;
import burp.Ui.Tags;

import java.util.List;
import java.util.ArrayList;

public class BurpAnalyzedRequest {
    private IBurpExtenderCallbacks callbacks;

    private IExtensionHelpers helpers;

    private CustomBurpHelpers customBurpHelpers;

    private IHttpRequestResponse requestResponse;

    private List<IParameter> eligibleParameters = new ArrayList<>();

    private Tags tags;

    public BurpAnalyzedRequest(IBurpExtenderCallbacks callbacks, Tags tags, IHttpRequestResponse requestResponse) {
        this.callbacks = callbacks;
        this.helpers = this.callbacks.getHelpers();

        this.tags = tags;

        this.customBurpHelpers = new CustomBurpHelpers(callbacks);

        this.requestResponse = requestResponse;

        initEligibleParameters();
    }

    public IHttpRequestResponse requestResponse() {
        return this.requestResponse;
    }

    public IRequestInfo analyzeRequest() {
        return this.helpers.analyzeRequest(this.requestResponse.getRequest());
    }

    /**
     * 初始化所有符合条件的参数
     */
    private void initEligibleParameters() {
        List<Integer> scanTypeList = this.tags.getBaseSettingTagClass().getScanTypeList();

        List<String> blackListParameters = this.tags.getBaseSettingTagClass().getBlackListParameters();

        if (analyzeRequest().getParameters().isEmpty()) {
            return;
        }

        if (blackListParameters == null || blackListParameters.size() <= 0) {
            for (IParameter p : analyzeRequest().getParameters()) {
                for (Integer type : scanTypeList) {
                    if (p.getType() == type) {
                        this.eligibleParameters.add(p);
                    }
                }
            }
        } else {
            for (IParameter p : analyzeRequest().getParameters()) {
                for (Integer type : scanTypeList) {
                    String name = p.getName().trim();
                    if (!CustomHelpers.listKeyExists(name, blackListParameters) && new Integer(p.getType()).equals(type)) {
                        this.eligibleParameters.add(p);
                    }
                }
            }
        }
    }

    /**
     * 获取所有符合条件的json参数
     *
     * @return List<IParameter>
     */
    public List<IParameter> getEligibleParameters() {
        return this.eligibleParameters;
    }

    /**
     * 判断站点是否有符合条件的参数
     *
     * @return
     */
    public Boolean isSiteEligibleParameters() {
        if (this.getEligibleParameters().size() > 0) {
            return true;
        }

        return false;
    }

    /**
     * 会根据程序类型自动组装请求的 请求发送接口
     *
     * @param p
     * @param payload
     * @param headers
     * @return
     */
    public IHttpRequestResponse makeHttpRequest(IParameter p, String payload, List<String> headers) {
        byte[] newRequest;

        // headers头处理
        List<String> newHeaders = new ArrayList<>();
        if (headers != null && headers.size() != 0) {
            for (String h : this.analyzeRequest().getHeaders()) {
                if (!CustomHelpers.listKeySearch(h.split(": ")[0], headers)) {
                    newHeaders.add(h);
                }
            }
            newHeaders.addAll(headers);
        } else {
            newHeaders = this.analyzeRequest().getHeaders();
        }

        // 数据处理
        if (this.analyzeRequest().getContentType() == 4 && p.getType() == 6) {
            // POST请求包提交的数据为json时的处理
            newRequest = this.buildHttpMessage(p, payload, newHeaders);
        } else {
            // 普通数据格式的处理
            newRequest = this.buildParameter(p, payload, newHeaders);
        }

        IHttpRequestResponse newHttpRequestResponse = this.callbacks.makeHttpRequest(this.requestResponse().getHttpService(), newRequest);
        return newHttpRequestResponse;
    }

    /**
     * json数据格式请求处理方法
     *
     * @param payload
     * @return
     */
    private byte[] buildHttpMessage(IParameter p, String payload, List<String> headers) {
        byte[] request = this.requestResponse().getRequest();
        String requestBody = this.customBurpHelpers.getHttpRequestBody(request);

        String pl = "\"" + p.getName() + "\"" + ":" + "\"" + payload + "\"";
        String pj1 = "\"" + p.getName() + "\"" + ":" + "\"" + p.getValue() + "\"";
        String pj2 = "\"" + p.getName() + "\"" + ":" + p.getValue();

        requestBody = requestBody.replace(pj1, pl);
        requestBody = requestBody.replace(pj2, pl);

        byte[] newRequest = this.helpers.buildHttpMessage(
                headers,
                requestBody.getBytes());
        return newRequest;
    }

    /**
     * 普通数据格式的参数构造方法
     *
     * @param p
     * @param payload
     * @return
     */
    private byte[] buildParameter(IParameter p, String payload, List<String> headers) {
        byte[] request = this.requestResponse().getRequest();
        String requestBody = this.customBurpHelpers.getHttpRequestBody(request);

        // 添加header头
        byte[] newRequest = this.helpers.buildHttpMessage(
                headers,
                requestBody.getBytes());

        IParameter newParameter = this.helpers.buildParameter(
                p.getName(),
                payload,
                p.getType()
        );

        return this.helpers.updateParameter(newRequest, newParameter);
    }
}