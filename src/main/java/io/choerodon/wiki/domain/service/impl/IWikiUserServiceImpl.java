package io.choerodon.wiki.domain.service.impl;

import java.io.IOException;

import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import retrofit2.Call;
import retrofit2.Response;

import io.choerodon.core.exception.CommonException;
import io.choerodon.wiki.domain.application.entity.WikiUserE;
import io.choerodon.wiki.domain.service.IWikiUserService;
import io.choerodon.wiki.infra.common.Stage;
import io.choerodon.wiki.infra.feign.WikiClient;

/**
 * Created by Ernst on 2018/7/5.
 */
@Service
public class IWikiUserServiceImpl implements IWikiUserService {

    private static final Logger logger = LoggerFactory.getLogger(IWikiUserServiceImpl.class);

    @Value("${wiki.client}")
    private String client;

    @Value("${wiki.default-group}")
    private String defaultGroup;

    private WikiClient wikiClient;

    public IWikiUserServiceImpl(WikiClient wikiClient) {
        this.wikiClient = wikiClient;
    }

    @Override
    public Boolean createUser(WikiUserE wikiUserE, String param1, String xmlParam, String username) {
        try {
            RequestBody requestBody = RequestBody.create(MediaType.parse(Stage.APPXML), xmlParam);
            Call<ResponseBody> call = wikiClient.createUser(username,
                    client, param1, requestBody);
            Response response = call.execute();
            if (response.code() == 201 || response.code() == 202) {
                return addUserToDefaultGroup(param1, username);
            } else {
                return false;
            }
        } catch (IOException e) {
            logger.error(e.getMessage());
            return false;
        }
    }

    private Boolean addUserToDefaultGroup(String param1, String username) throws IOException {
        FormBody body = new FormBody.Builder().add("className", "XWiki.XWikiGroups").add("property#member", "XWiki." + param1).build();
        Call<ResponseBody> addGroupCall = wikiClient.createGroupUsers(username, client, defaultGroup, body);
        Response addGroupResponse = addGroupCall.execute();
        return addGroupResponse.code() == 201 ? true : false;
    }

    @Override
    public Boolean checkDocExsist(String username, String param1) {
        Call<ResponseBody> call = wikiClient.checkDocExsist(username,
                client, param1);
        try {
            Response response = call.execute();
            if (response.code() == 200) {
                return true;
            } else if (response.code() == 404) {
                return false;
            } else {
                throw new CommonException("error.get.user");
            }
        } catch (IOException e) {
            logger.error(e.getMessage());
            throw new CommonException("error.get.user");
        }
    }

    @Override
    public Boolean deletePage(String pageName, String username) {
        Call<ResponseBody> call = wikiClient.deletePage(username,
                client, pageName);
        try {
            Response response = call.execute();
            if (response.code() == 204) {
                return true;
            } else if (response.code() == 404) {
                throw new CommonException("error.get.page");
            } else {
                throw new CommonException("error.delete.page");
            }
        } catch (IOException e) {
            logger.error(e.getMessage());
            throw new CommonException("error.delete.page");
        }
    }

}
