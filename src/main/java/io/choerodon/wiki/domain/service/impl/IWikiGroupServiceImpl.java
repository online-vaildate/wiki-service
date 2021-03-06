package io.choerodon.wiki.domain.service.impl;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import io.choerodon.wiki.domain.service.IWikiGroupService;
import io.choerodon.wiki.infra.common.FileUtil;
import io.choerodon.wiki.infra.common.Stage;
import io.choerodon.wiki.infra.feign.WikiClient;

/**
 * Created by Ernst on 2018/7/6.
 */
@Service
public class IWikiGroupServiceImpl implements IWikiGroupService {

    private static final Logger logger = LoggerFactory.getLogger(IWikiGroupServiceImpl.class);

    @Value("${wiki.client}")
    private String client;

    private WikiClient wikiClient;

    private IWikiUserServiceImpl iWikiUserService;

    public IWikiGroupServiceImpl(WikiClient wikiClient, IWikiUserServiceImpl iWikiUserService) {
        this.wikiClient = wikiClient;
        this.iWikiUserService = iWikiUserService;
    }

    @Override
    public Boolean createGroup(String groupName, String username) {
        try {
            RequestBody requestBody = RequestBody.create(MediaType.parse(Stage.APPXML), getGroupXml());
            Call<ResponseBody> call = wikiClient.createGroup(username,
                    client, groupName, requestBody);
            Response response = call.execute();
            if (response.code() == 201 || response.code() == 202) {
                return true;
            } else {
                throw new CommonException("error.create.group");
            }
        } catch (IOException e) {
            logger.error(e.getMessage());
            throw new CommonException("error.create.group");
        }
    }

    @Override
    public Boolean createGroupUsers(String groupName, String loginName, String username) {
        try {
            //如果组不存在则新建组
            Boolean flag = iWikiUserService.checkDocExsist(username, groupName);
            if (logger.isDebugEnabled()) {
                logger.debug("Does the group exist? " + flag);
            }
            if (!flag) {
                this.createGroup(groupName, username);
            }

            FormBody body = new FormBody.Builder().add("className", "XWiki.XWikiGroups").add("property#member", "XWiki." + loginName).build();
            Call<ResponseBody> call = wikiClient.createGroupUsers(username, client, groupName, body);
            Response response = call.execute();
            if (logger.isDebugEnabled()) {
                logger.debug("Create the code returned by the group user : " + response.code());
            }
            if (response.code() == 201) {
                return true;
            }
        } catch (IOException e) {
            logger.error(e.getMessage());
        }
        return false;
    }

    @Override
    public Boolean disableOrgGroupView(String organizationCode, String organizationName, String username) {
        try {
            String groupName = "O-" + organizationCode + Stage.USER_GROUP;
            Boolean falg = iWikiUserService.checkDocExsist(username, groupName);
            if (!falg) {
                throw new CommonException(Stage.ERROR_QUERY_GROUP);
            }

            Call<ResponseBody> call = wikiClient.offerRightToOrgGroupView(username, client,
                    "O-" + organizationName, getBody(groupName, "0", "view"));
            Response response = call.execute();
            if (response.code() == 201) {
                return true;
            }
        } catch (IOException e) {
            logger.error(e.getMessage());
        }

        return false;
    }

    @Override
    public Boolean disableProjectGroupView(String projectName, String projectCode, String organizationName, String username) {
        try {
            String groupName = "P-" + projectCode + Stage.USER_GROUP;
            Boolean falg = iWikiUserService.checkDocExsist(username, groupName);
            if (!falg) {
                throw new CommonException(Stage.ERROR_QUERY_GROUP);
            }

            Call<ResponseBody> call = wikiClient.offerRightToProjectGroupView(username, client,
                    "O-" + organizationName, "P-" + projectName, getBody(groupName, "0", "view"));
            Response response = call.execute();
            if (response.code() == 201) {
                return true;
            }
        } catch (IOException e) {
            logger.error(e.getMessage());
        }

        return false;
    }

    @Override
    public Boolean addRightsToOrg(String organizationCode, String organizationName, List<String> rights, Boolean isAdmin, String username) {
        try {
            String groupName = "O-" + organizationCode + (isAdmin ? Stage.ADMIN_GROUP : Stage.USER_GROUP);
            Boolean falg = iWikiUserService.checkDocExsist(username, groupName);
            if (!falg) {
                throw new CommonException(Stage.ERROR_QUERY_GROUP);
            }
            StringBuilder stringBuilder = new StringBuilder();
            for (String right : rights) {
                stringBuilder.append(right);
                stringBuilder.append(",");
            }
            String levels = stringBuilder.toString();
            levels = levels.substring(0, levels.length() - 1);

            String encodeStr = "O-" + organizationName;
            URLEncoder.encode(encodeStr, "UTF-8");
            Call<ResponseBody> call = wikiClient.offerRightToOrgGroupView(username, client, encodeStr, getBody(groupName, "1", levels));
            Response response = call.execute();

            if (response.code() == 201) {
                return true;
            }
        } catch (IOException e) {
            logger.error(e.getMessage());
        }
        return false;
    }

    @Override
    public Boolean addRightsToProject(String projectName, String projectCode, String organizationName, List<String> rights, Boolean isAdmin, String username) {
        try {
            String groupName = "P-" + projectCode + (isAdmin ? Stage.ADMIN_GROUP : Stage.USER_GROUP);
            Boolean falg = iWikiUserService.checkDocExsist(username, groupName);
            if (!falg) {
                throw new CommonException(Stage.ERROR_QUERY_GROUP);
            }

            StringBuilder stringBuilder = new StringBuilder();
            for (String right : rights) {
                stringBuilder.append(right);
                stringBuilder.append(",");
            }
            String levels = stringBuilder.toString();
            levels = levels.substring(0, levels.length() - 1);

            Call<ResponseBody> call = wikiClient.offerRightToProjectGroupView(username, client, "O-" + organizationName,
                    "P-" + projectName, getBody(groupName, "1", levels));
            Response response = call.execute();
            if (response.code() == 201) {
                return true;
            }
        } catch (IOException e) {
            logger.error(e.getMessage());
        }
        return false;
    }

    private String getGroupXml() {
        InputStream inputStream = this.getClass().getResourceAsStream("/xml/group.xml");
        Map<String, String> params = new HashMap<>();
        return FileUtil.replaceReturnString(inputStream, params);
    }

    private FormBody getBody(String groupName, String allow, String levels) {
        return new FormBody.Builder().add("className", "XWiki.XWikiGlobalRights").add("property#allow", allow)
                .add("property#groups", "XWiki." + groupName).add("property#levels", levels).build();
    }
}
