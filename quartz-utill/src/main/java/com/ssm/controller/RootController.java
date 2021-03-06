package com.ssm.controller;

import com.alibaba.fastjson.JSONObject;
import com.ssm.config.quartz.QuartzManager;
import com.ssm.model.TaskModel;
import com.ssm.model.UserModel;
import com.ssm.service.UserService;
import com.ssm.service.impl.MyJob;
import com.ssm.utils.CaptchaUtil;
import com.ssm.utils.HttpRequest;
import com.ssm.utils.ToolJson;
import com.ssm.utils.WXUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import sun.misc.BASE64Decoder;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Date;
import java.util.Map;

/**
 * Job有状态实现类，不允许并发执行
 * 若一个方法一次执行不完下次轮转时则等待该方法执行完后才执行下一次操作
 * 主要是通过注解：@DisallowConcurrentExecution和@PersistJobDataAfterExecution
 *
 * @author FaceFeel
 * @Created 2018-04-27 11:21
 **/
@Controller
@RequestMapping("/")
public class RootController {

    //region 变量定义部分
    private static final Logger logger = LoggerFactory.getLogger(RootController.class);
    @Autowired
    private UserService userService;
    //endregion

    //region 定时器部分
    @RequestMapping("startTask")
    @ResponseBody
    public String startTask(TaskModel taskModel) {
        try {
            QuartzManager.addJob(taskModel.getJobName(), taskModel.getJobGroupName(), taskModel.getTriggerName(), taskModel.getTriggerGroupName(), MyJob.class, taskModel.getCron());
        } catch (Exception e) {
            logger.error("startTask error,info is: " + e);
            return "0";
        }
        return "1";
    }

    @RequestMapping("modifyTask")
    @ResponseBody
    public String modifyTask(TaskModel taskModel) {
        try {
            QuartzManager.modifyJobTime(taskModel.getTriggerName(), taskModel.getTriggerGroupName(), "0/5 * * * * ?");
        } catch (Exception e) {
            logger.error("modifyTask error,info is: " + e);
            return "0";
        }
        return "1";
    }

    @RequestMapping("shutTask")
    @ResponseBody
    public String shutTask(TaskModel taskModel) {
        try {
            QuartzManager.removeJob(taskModel.getJobName(), taskModel.getJobGroupName(), taskModel.getTriggerName(), taskModel.getTriggerGroupName());
        } catch (Exception e) {
            logger.error("shutTask error,info is: " + e);
            return "0";
        }
        return "1";
    }

    /**
     * 暂停指定的任务
     *
     * @param taskModel 定时器实体
     */
    @RequestMapping("pauseTask")
    @ResponseBody
    public String pauseTask(TaskModel taskModel) {
        try {
            QuartzManager.pauseJob(taskModel.getJobName(), taskModel.getJobGroupName());
        } catch (Exception e) {
            logger.error("pauseTask error,info is: " + e);
            return "0";
        }
        return "1";
    }

    /**
     * 恢复指定的任务
     *
     * @param taskModel 定时器实体
     */
    @RequestMapping("resumeTask")
    @ResponseBody
    public String resumeJob(TaskModel taskModel) {
        try {
            QuartzManager.resumeJob(taskModel.getJobName(), taskModel.getJobGroupName());
        } catch (Exception e) {
            logger.error("resumeTask error,info is: " + e);
            return "0";
        }
        return "1";
    }

    @RequestMapping("shutAllTask")
    @ResponseBody
    public String shutAllTask() {
        try {
            QuartzManager.shutdownJobs();
        } catch (Exception e) {
            logger.error("shutAllTask error,info is: " + e);
            return "0";
        }
        return "1";
    }

    @RequestMapping("startAllTask")
    @ResponseBody
    public String startAllTask() {
        try {
            QuartzManager.startJobs();
        } catch (Exception e) {
            logger.error("shutAllTask error,info is: " + e);
            return "0";
        }
        return "1";
    }
    //endregion

    @RequestMapping("weixintest")
    @ResponseBody
    public JSONObject weixintest(String data,String vi,String code) {

        String appSecret = "";
        String appID = "";
        String requestUrl = "https://api.weixin.qq.com/sns/jscode2session?appid="+ appID +"&secret="+ appSecret +"&js_code="+ code +"&grant_type=authorization_code";
        String result = HttpRequest.httpClientGet(requestUrl);
        Map map = ToolJson.jsonToMap(result, Map.class);
        String session_key = map.get("session_key").toString();
        String openid = map.get("openid").toString();
        return WXUtils.decodeCrytedData(data, session_key, vi);
    }

    @RequestMapping("login")
    public String login() {

        UserModel admin = userService.findUserByUserName("admin");
        if (admin == null) {
            logger.info("管理员初始化成功");
        }
        return "root/task";
    }

    /**
     * 注销登录
     *
     * @param request http
     * @return ""
     */
    @RequestMapping("logout")
    public String logout(HttpServletRequest request) {

        request.getSession().setAttribute("currentUser", null);
        return "redirect:/login";
    }

    @RequestMapping("index")
    @ResponseBody
    public String index(HttpServletRequest request, UserModel userModel) {

        //在这判断登录
        UserModel userByUserName = userService.findUserByUserNameAndPassWord(userModel.getUserName(), userModel.getPassWord());
        if (userByUserName != null) {
            //把当前登录成功的用户保存到会话中
            ToolController.saveCurrentUser(request, userByUserName);
//            request.getSession().setAttribute("currentUser", userByUserName);
            //记录操作日志

            //记录最近登录时间
            userService.updateUser(new UserModel().setId(userByUserName.getId()).setLoginDate(new Date()));

            //判断权限
            if (userByUserName.getAuth() == 0) {
                //普通用户
                return "0";
            }
            //管理员
            return "1";
        }
        //登录失败跳回原来的页面
        return "2";
    }

    @RequestMapping("uploadPage")
    public String uploadPage() {
        return "root/camera";
    }

    @RequestMapping("upload")
    @ResponseBody
    public String upload(HttpServletRequest request) {

        String basePath = "upload/";
        String filePath = request.getSession().getServletContext().getRealPath("/") + basePath;
//        String filePath = "D:/upload/";
        String fileName = (new Date()).getTime() + ".png";
        String imgStr = request.getParameter("image");

        if (null != imgStr) {
            imgStr = imgStr.substring(imgStr.indexOf(",") + 1);
        }
        boolean flag = GenerateImage(imgStr, filePath, fileName);
        String result = "";
        if (flag) {
//            result = filePath + fileName;
            result = basePath + fileName;
        }
        return result;
    }

    @RequestMapping("delPhoto")
    @ResponseBody
    public String delPhoto(String filePath, HttpServletRequest request) {

        String basePath = request.getSession().getServletContext().getRealPath("/");
        File file = new File(basePath + filePath);
        if (!file.isFile()) {
            return "0";
        }

        boolean delete = file.delete();
        if (!delete) {
            return "0";
        }
        return "1";
    }

    private boolean GenerateImage(String imgStr, String filePath, String fileName) {
        try {
            if (imgStr == null) {
                return false;
            }
            BASE64Decoder decoder = new BASE64Decoder();
            byte[] b = decoder.decodeBuffer(imgStr);
            File file = new File(filePath);
            if (!file.exists()) {
                file.mkdirs();
            }
            OutputStream out = new FileOutputStream(filePath + fileName);
            out.write(b);
            out.flush();
            out.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    //region 登录验证码生成部分

    /**
     * 前端请求的入口
     *
     * @param request  请求
     * @param response 响应
     * @throws IOException 异常
     */
    @RequestMapping(value = "captcha", method = RequestMethod.GET)
    @ResponseBody
    public void captcha(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        CaptchaUtil.outputCaptcha(request, response);
    }

    @RequestMapping("checkCode")
    @ResponseBody
    public String checkCode(HttpServletRequest request) {

        String string = request.getSession().getAttribute("randomString").toString();
        String code = request.getParameter("code");
        if (string.equals(code)) {
            return "1";
        } else {
            return "0";
        }
    }
    //endregion
}
