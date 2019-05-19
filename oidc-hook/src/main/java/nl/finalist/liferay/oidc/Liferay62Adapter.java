package nl.finalist.liferay.oidc;

import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.exception.SystemException;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.util.LocaleUtil;
import com.liferay.portal.kernel.util.StringPool;
import com.liferay.portal.model.User;
import com.liferay.portal.model.UserGroup;
import com.liferay.portal.service.ServiceContext;
import com.liferay.portal.service.UserGroupLocalServiceUtil;
import com.liferay.portal.service.UserLocalServiceUtil;
import com.liferay.portal.util.PortalUtil;
import com.liferay.util.PwdGenerator;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class Liferay62Adapter implements LiferayAdapter {

    private static final Log LOG = LogFactoryUtil.getLog(Liferay62Adapter.class);

    @Override
    public OIDCConfiguration getOIDCConfiguration(long companyId) {
        return new OpenIDConnectPortalPropsConfiguration(companyId);
    }

    @Override
    public LiferaySitesConfiguration getLiferaySitesConfiguration() {
        return new LiferaySitesPropsConfiguration();
    }

    @Override
    public void trace(String s) {
        LOG.trace(s);
    }

    @Override
    public void info(String s) {
        LOG.info(s);
    }

    @Override
    public void debug(String s) {
        LOG.debug(s);
    }

    @Override
    public void warn(String s) {
        LOG.warn(s);
    }

    @Override
    public String getCurrentCompleteURL(HttpServletRequest request) {
        return PortalUtil.getCurrentCompleteURL(request);
    }

    @Override
    public boolean isUserLoggedIn(HttpServletRequest request) {
        try {
            return PortalUtil.getUser(request) != null;
        } catch (PortalException | SystemException e) {
            return false;
        }
    }

    @Override
    public long getCompanyId(HttpServletRequest request) {
        return PortalUtil.getCompanyId(request);
    }

    @Override
    public void error(String s) {
        LOG.error(s);
    }

    @Override
    public String createOrUpdateUser(long companyId, String emailAddress, String firstName, String lastName) {
        return null;
    }

    @Override
    public String createOrUpdateUser(long companyId, String emailAddress, String firstName, String lastName, ArrayList<String> roles) {

        try {
            LOG.debug("Received emailAddress = " + emailAddress);
            LOG.debug("Received companyId = " + companyId);

            User user = UserLocalServiceUtil.fetchUserByEmailAddress(companyId, emailAddress);
            List<UserGroup> userGroupRoles = UserGroupLocalServiceUtil.getUserGroups(companyId);

            List<Long> userGroupsId = mapRoleToUserGroupId(roles, userGroupRoles);


            //FIXME Guest user seems to not have firstname and lastname
            if (firstName == null || firstName.isEmpty()){
                firstName = "testFirst";
            }

            if (lastName == null || lastName.isEmpty()){
                lastName = "testLast";
            }

            if (user == null) {
                LOG.info("No Liferay user found with email address " + emailAddress + ", will create one.");
                user = addUser(companyId, emailAddress, firstName, lastName, userGroupsId);
            } else {
                LOG.info("User found, updating name details with info from userinfo");
                updateUser(user, firstName, lastName, userGroupsId);
            }

            LOG.info(user.getUserId());
            return String.valueOf(user.getUserId());

        } catch (SystemException | PortalException e) {
            throw new RuntimeException(e);
        }
    }

    private List<Long> mapRoleToUserGroupId(ArrayList<String> roles, List<UserGroup> userGroupRoles) {
        List<Long> userGroupsId = new ArrayList<>();

        for (UserGroup userGroup : userGroupRoles) {

            if (roles.contains(userGroup.getName())) {
                userGroupsId.add(userGroup.getUserGroupId());
            }

        }

        return userGroupsId;
    }


    private long[] toLongArray(List<Long> arraylist){
        long[] longArray = new long[arraylist.size()];
        for(int i = 0;i < longArray.length;i++)
            longArray[i] = arraylist.get(i);

        return longArray;
    }


    // Copied from OpenSSOAutoLogin.java
    protected User addUser(
            long companyId, String emailAddress, String firstName, String lastName, List<Long> usergroups)
            throws SystemException, PortalException {

        Locale locale = LocaleUtil.getMostRelevantLocale();
        long creatorUserId = 0;
        boolean autoPassword = false;
        String password1 = PwdGenerator.getPassword();
        String password2 = password1;
        boolean autoScreenName = true;
        String screenName = "not_used_but_autogenerated_instead";
        long facebookId = 0;
        String openId = StringPool.BLANK;
        String middleName = StringPool.BLANK;
        int prefixId = 0;
        int suffixId = 0;
        boolean male = true;
        int birthdayMonth = Calendar.JANUARY;
        int birthdayDay = 1;
        int birthdayYear = 1970;
        String jobTitle = StringPool.BLANK;
        long[] groupIds = null;
        long[] organizationIds = null;
        long[] roleIds = null;
        long[] userGroupIds = toLongArray(usergroups);
        boolean sendEmail = false;
        ServiceContext serviceContext = new ServiceContext();

        User user = UserLocalServiceUtil.addUser(
                creatorUserId, companyId, autoPassword, password1, password2,
                autoScreenName, screenName, emailAddress, facebookId, openId,
                locale, firstName, middleName, lastName, prefixId, suffixId, male,
                birthdayMonth, birthdayDay, birthdayYear, jobTitle, groupIds,
                organizationIds, roleIds, userGroupIds, sendEmail, serviceContext);

        // No password
        user.setPasswordReset(false);
        // No reminder query at first login.
        user.setReminderQueryQuestion("x");
        user.setReminderQueryAnswer("y");
        UserLocalServiceUtil.updateUser(user);
        return user;
    }


    private void updateUser(User user, String firstName, String lastName, List<Long> userGroups) {
        user.setFirstName(firstName);
        user.setLastName(lastName);

        try {

            for (Long id : userGroups){
                UserLocalServiceUtil.addUserGroupUser(id, user);
            }

            UserLocalServiceUtil.updateUser(user);
        } catch (SystemException e) {
            LOG.error("Could not update user with new name attributes", e);
        }
    }
}
