/*
 *  Copyright (C) [SonicCloudOrg] Sonic Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package org.cloud.sonic.controller.services.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.cloud.sonic.controller.mapper.*;
import org.cloud.sonic.controller.models.base.CommentPage;
import org.cloud.sonic.controller.models.base.TypeConverter;
import org.cloud.sonic.controller.models.domain.PublicSteps;
import org.cloud.sonic.controller.models.domain.PublicStepsSteps;
import org.cloud.sonic.controller.models.domain.Steps;
import org.cloud.sonic.controller.models.domain.StepsElements;
import org.cloud.sonic.controller.models.dto.ElementsDTO;
import org.cloud.sonic.controller.models.dto.StepsDTO;
import org.cloud.sonic.controller.models.enums.ConditionEnum;
import org.cloud.sonic.controller.models.http.StepSort;
import org.cloud.sonic.controller.services.StepsService;
import org.cloud.sonic.controller.services.impl.base.SonicServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author ZhouYiXun
 * @des 测试步骤实现
 * @date 2021/8/20 17:51
 */
@Service
public class StepsServiceImpl extends SonicServiceImpl<StepsMapper, Steps> implements StepsService {

    @Autowired private StepsMapper stepsMapper;
    @Autowired private ElementsMapper elementsMapper;
    @Autowired private PublicStepsMapper publicStepsMapper;
    @Autowired private PublicStepsStepsMapper publicStepsStepsMapper;
    @Autowired private StepsElementsMapper stepsElementsMapper;

    @Transactional
    @Override
    public List<StepsDTO> findByCaseIdOrderBySort(int caseId) {

        // 取出用例下所有无父级的步骤
        List<StepsDTO> stepsDTOList = lambdaQuery()
                .eq(Steps::getCaseId, caseId)
                .eq(Steps::getParentId, 0)
                .orderByAsc(Steps::getSort)
                .list()
                // 转换成DTO
                .stream().map(TypeConverter::convertTo).collect(Collectors.toList());

        // 遍历父级步骤，如果是条件步骤，则取出子步骤集合
        handleSteps(stepsDTOList);

        return stepsDTOList;
    }

    @Transactional
    @Override
    public List<StepsDTO> handleSteps(List<StepsDTO> stepsDTOS) {
        if (CollectionUtils.isEmpty(stepsDTOS)) {
            return stepsDTOS;
        }
        for (StepsDTO stepsDTO : stepsDTOS) {
            handleStep(stepsDTO);
        }
        return stepsDTOS;
    }


    /**
     * 获取每个step下的childSteps 组装成一个list返回
     * @param stepsDTOS 步骤集合
     * @return 包含所有子步骤的集合
     */
    public  List<StepsDTO> getChildSteps(List<StepsDTO> stepsDTOS) {

        // 记录一下层级，第一层不要
        List<StepsDTO> childSteps = new ArrayList<>();

        if (stepsDTOS == null || stepsDTOS.isEmpty()) {
            // 为空说明递归到最后一层，直接返回空集合
            return childSteps;
        }
        for (StepsDTO stepsDTO : stepsDTOS) {
            // 递归调用，底层返回的集合直接add到上层来
            if (stepsDTO.getChildSteps() != null) {
                childSteps.add(stepsDTO);
                childSteps.addAll(stepsDTO.getChildSteps());
                getChildSteps(stepsDTO.getChildSteps());
            }
        }
        // 结束递归的集合，最外层的就是结果
        return childSteps;
    }

//    //插入子步骤步骤
//    public boolean insertSteps(List<StepsDTO> stepsDTOS){
//        List<Steps> stepsDTOList = getChildSteps(stepsDTOS)
//                .stream().map(TypeConverter::convertTo).collect(Collectors.toList());
//        for(Steps steps : stepsDTOList){
//            steps.setId(null);
//            save(steps);
//        }
//        return true;
//    }


    @Transactional
    @Override
    public StepsDTO handleStep(StepsDTO stepsDTO) {
        if (stepsDTO == null) {
            return null;
        }
        stepsDTO.setElements(elementsMapper.listElementsByStepsId(stepsDTO.getId()));
        // 如果是条件步骤
        if (!stepsDTO.getConditionType().equals(ConditionEnum.NONE.getValue())) {
            List<StepsDTO> childSteps = lambdaQuery()
                    .eq(Steps::getParentId, stepsDTO.getId())
                    .orderByAsc(Steps::getSort)
                    .list()
                    // 转换成DTO
                    .stream().map(TypeConverter::convertTo).collect(Collectors.toList());
            stepsDTO.setChildSteps(handleSteps(childSteps));
        }
        return stepsDTO;
    }

    @Override
    public boolean resetCaseId(int id) {
        if (existsById(id)) {
            Steps steps = baseMapper.selectById(id);
            steps.setCaseId(0);
            save(steps);
            return true;
        } else {
            return false;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean delete(int id) {
        if (existsById(id)) {
            Steps steps = baseMapper.selectById(id);
            publicStepsStepsMapper.delete(new QueryWrapper<PublicStepsSteps>().eq("steps_id", steps.getId()));
            baseMapper.deleteById(id);
            return true;
        } else {
            return false;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveStep(StepsDTO stepsDTO) {
        if (stepsDTO.getStepType().equals("publicStep")) {
            PublicSteps publicSteps = publicStepsMapper.selectById(Integer.parseInt(stepsDTO.getText()));
            if (publicSteps != null) {
                stepsDTO.setContent(publicSteps.getName());
            } else {
                stepsDTO.setContent("unknown");
            }
        }

        // 设置排序为最后
        if (!existsById(stepsDTO.getId())) {
            stepsDTO.setSort(stepsMapper.findMaxSort() + 1);
        }
        // 子步骤的caseId跟随父步骤的
        Steps parent = getById(stepsDTO.getParentId());
        if (!ObjectUtils.isEmpty(parent)) {
            stepsDTO.setCaseId(parent.getCaseId());
        }
        Steps steps = stepsDTO.convertTo();
        save(steps);

        // 删除旧关系
        stepsElementsMapper.delete(new LambdaQueryWrapper<StepsElements>().eq(StepsElements::getStepsId, steps.getId()));

        // 保存element映射关系
        List<ElementsDTO> elements = stepsDTO.getElements();
        for (ElementsDTO element : elements) {
            stepsElementsMapper.insert(new StepsElements().setElementsId(element.getId()).setStepsId(steps.getId()));
        }
    }

    @Transactional
    @Override
    public StepsDTO findById(int id) {
        StepsDTO stepsDTO = baseMapper.selectById(id).convertTo();
        handleStep(stepsDTO);
        return stepsDTO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sortSteps(StepSort stepSort) {

        List<Steps> stepsList = lambdaQuery().eq(Steps::getCaseId, stepSort.getCaseId())
                // <=
                .le(Steps::getSort, stepSort.getStartId())
                // >=
                .ge(Steps::getSort, stepSort.getEndId())
                .orderByAsc(Steps::getSort)
                .list();

        if (stepSort.getDirection().equals("down")) {
            for (int i = 0; i < stepsList.size() - 1; i++) {
                int temp = stepsList.get(stepsList.size() - 1).getSort();
                stepsList.get(stepsList.size() - 1).setSort(stepsList.get(i).getSort());
                stepsList.get(i).setSort(temp);
            }
        } else {
            for (int i = 0; i < stepsList.size() - 1; i++) {
                int temp = stepsList.get(0).getSort();
                stepsList.get(0).setSort(stepsList.get(stepsList.size() - 1 - i).getSort());
                stepsList.get(stepsList.size() - 1 - i).setSort(temp);
            }
        }
        saveOrUpdateBatch(stepsList);
    }

    @Override
    public CommentPage<StepsDTO> findByProjectIdAndPlatform(int projectId, int platform, Page<Steps> pageable) {

        Page<Steps> page = lambdaQuery().eq(Steps::getProjectId, projectId)
                .eq(Steps::getPlatform, platform)
                .eq(Steps::getParentId, 0)
                .orderByDesc(Steps::getId)
                .page(pageable);

        List<StepsDTO> stepsDTOList = page.getRecords()
                .stream().map(TypeConverter::convertTo).collect(Collectors.toList());
        handleSteps(stepsDTOList);

        return CommentPage.convertFrom(page, stepsDTOList);
    }

    @Override
    public List<Steps> listStepsByElementsId(int elementsId) {
        return stepsMapper.listStepsByElementId(elementsId);
    }

    @Override
    public boolean deleteByProjectId(int projectId) {
        return baseMapper.delete(new LambdaQueryWrapper<Steps>().eq(Steps::getProjectId, projectId)) > 0;
    }

    @Override
    public List<StepsDTO> listByPublicStepsId(int publicStepsId) {
        return stepsMapper.listByPublicStepsId(publicStepsId)
                // 填充elements
                .stream().map(e -> e.convertTo().setElements(elementsMapper.listElementsByStepsId(e.getId())))
                .collect(Collectors.toList());
    }
    /**
     * 步骤列表:搜索
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommentPage<StepsDTO> searchFindByProjectIdAndPlatform(int projectId, int platform,int page ,int pageSize,
                                                                  String searchContent){
        Page<Steps> pageList = new Page<>(page,pageSize);
        //分页返回数据
        IPage<Steps> steps = stepsMapper.sreachByEleName(pageList,searchContent);
        //取出页面里面的数据，转为List<StepDTO>
        List<StepsDTO> stepsDTOList = steps.getRecords()
                .stream().map(TypeConverter::convertTo).collect(Collectors.toList());

        handleSteps(stepsDTOList);

        return CommentPage.convertFrom(pageList, stepsDTOList);
    }
}
