package com.ctrip.zeus.service.model.impl;

import com.ctrip.zeus.exceptions.ValidationException;
import com.ctrip.zeus.model.entity.Domain;
import com.ctrip.zeus.model.entity.VirtualServer;
import com.ctrip.zeus.service.model.ArchiveService;
import com.ctrip.zeus.service.model.ModelMode;
import com.ctrip.zeus.service.model.VirtualServerRepository;
import com.ctrip.zeus.service.model.handler.SlbQuery;
import com.ctrip.zeus.service.model.handler.SlbValidator;
import com.ctrip.zeus.service.model.handler.VirtualServerValidator;
import com.ctrip.zeus.service.model.handler.impl.VirtualServerEntityManager;
import com.ctrip.zeus.service.nginx.CertificateService;
import com.ctrip.zeus.service.model.IdVersion;
import com.ctrip.zeus.service.query.VirtualServerCriteriaQuery;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.*;

/**
 * Created by zhoumy on 2015/7/27.
 */
@Component("virtualServerRepository")
public class VirtualServerRepositoryImpl implements VirtualServerRepository {
    @Resource
    private VirtualServerCriteriaQuery virtualServerCriteriaQuery;
    @Resource
    private VirtualServerEntityManager virtualServerEntityManager;
    @Resource
    private ArchiveService archiveService;
    @Resource
    private VirtualServerValidator virtualServerModelValidator;
    @Resource
    private SlbValidator slbModelValidator;
    @Resource
    private SlbQuery slbQuery;
    @Resource
    private CertificateService certificateService;

    @Override
    public List<VirtualServer> listAll(Long[] vsIds) throws Exception {
        return listAll(vsIds, ModelMode.MODEL_MODE_MERGE);
    }

    @Override
    public List<VirtualServer> listAll(Long[] vsIds, ModelMode mode) throws Exception {
        return archiveService.getVirtualServersByMode(vsIds, mode);
    }

    @Override
    public VirtualServer getById(Long vsId) throws Exception {
        return getById(vsId, ModelMode.MODEL_MODE_MERGE);
    }

    @Override
    public VirtualServer getById(Long vsId, ModelMode mode) throws Exception {
        return archiveService.getVirtualServerByMode(vsId, mode);
    }

    @Override
    public VirtualServer add(Long slbId, VirtualServer virtualServer) throws Exception {
        virtualServer.setSlbId(slbId);
        for (Domain domain : virtualServer.getDomains()) {
            domain.setName(domain.getName().toLowerCase());
        }
        Set<Long> retained = new HashSet<>();
        for (IdVersion idVersion : virtualServerCriteriaQuery.queryBySlbId(slbId)) {
            retained.add(idVersion.getId());
        }
        List<VirtualServer> check = listAll(retained.toArray(new Long[retained.size()]), ModelMode.MODEL_MODE_REDUNDANT);
        check.add(virtualServer);
        virtualServerModelValidator.validateVirtualServers(check);
        virtualServerEntityManager.add(virtualServer);

        if (virtualServer.getSsl().booleanValue()) {
            installCertificate(virtualServer);
        }
        return virtualServer;
    }

    @Override
    public void update(VirtualServer virtualServer) throws Exception {
        if (!virtualServerModelValidator.exists(virtualServer.getId()))
            throw new ValidationException("Virtual server with id " + virtualServer.getId() + " does not exist.");
        for (Domain domain : virtualServer.getDomains()) {
            domain.setName(domain.getName().toLowerCase());
        }
        Set<Long> retained = new HashSet<>();
        for (IdVersion idVersion : virtualServerCriteriaQuery.queryBySlbId(virtualServer.getSlbId())) {
            retained.add(idVersion.getId());
        }
        if (retained.size() == 0) {
            if (!slbModelValidator.exists(virtualServer.getSlbId())) {
                throw new ValidationException("Slb with id " + virtualServer.getSlbId() + " does not exist.");
            }
        }
        List<VirtualServer> check = listAll(retained.toArray(new Long[retained.size()]), ModelMode.MODEL_MODE_REDUNDANT);
        Iterator<VirtualServer> iter = check.iterator();
        while (iter.hasNext()) {
            VirtualServer c = iter.next();
            if (c.getId().equals(virtualServer.getId()) && c.getVersion().equals(virtualServer.getVersion())) {
                iter.remove();
                break;
            }
        }
        check.add(virtualServer);
        virtualServerModelValidator.validateVirtualServers(check);
        virtualServerEntityManager.update(virtualServer);

        if (virtualServer.getSsl().booleanValue()) {
            installCertificate(virtualServer);
        }
    }

    @Override
    public void delete(Long virtualServerId) throws Exception {
        virtualServerModelValidator.removable(getById(virtualServerId));
        virtualServerEntityManager.delete(virtualServerId);
    }

    @Override
    public void installCertificate(VirtualServer virtualServer) throws Exception {
        List<String> ips = slbQuery.getSlbIps(virtualServer.getSlbId());
        List<Domain> vsDomains = virtualServer.getDomains();
        String[] domains = new String[vsDomains.size()];
        for (int i = 0; i < domains.length; i++) {
            domains[i] = vsDomains.get(i).getName();
        }
        Long certId = certificateService.getCertificateOnBoard(domains);
        certificateService.install(virtualServer.getId(), ips, certId);
    }
}
