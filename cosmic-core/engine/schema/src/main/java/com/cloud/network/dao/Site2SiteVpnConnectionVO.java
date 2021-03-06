package com.cloud.network.dao;

import com.cloud.legacymodel.InternalIdentity;
import com.cloud.network.Site2SiteVpnConnection;
import com.cloud.utils.db.GenericDao;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = ("s2s_vpn_connection"))
public class Site2SiteVpnConnectionVO implements Site2SiteVpnConnection, InternalIdentity {
    @Column(name = "display", nullable = false)
    protected boolean display = true;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private long id;
    @Column(name = "uuid")
    private String uuid;
    @Column(name = "vpn_gateway_id")
    private long vpnGatewayId;
    @Column(name = "customer_gateway_id")
    private long customerGatewayId;
    @Column(name = "state")
    @Enumerated(value = EnumType.STRING)
    private State state;
    @Column(name = "domain_id")
    private Long domainId;
    @Column(name = "account_id")
    private Long accountId;
    @Column(name = GenericDao.CREATED_COLUMN)
    private Date created;
    @Column(name = GenericDao.REMOVED_COLUMN)
    private Date removed;
    @Column(name = "passive")
    private boolean passive;

    public Site2SiteVpnConnectionVO() {
    }

    public Site2SiteVpnConnectionVO(final long accountId, final long domainId, final long vpnGatewayId, final long customerGatewayId, final boolean passive) {
        uuid = UUID.randomUUID().toString();
        setVpnGatewayId(vpnGatewayId);
        setCustomerGatewayId(customerGatewayId);
        setState(State.Pending);
        this.accountId = accountId;
        this.domainId = domainId;
        this.passive = passive;
    }

    @Override
    public long getId() {
        return id;
    }

    @Override
    public String getUuid() {
        return uuid;
    }

    @Override
    public long getVpnGatewayId() {
        return vpnGatewayId;
    }

    public void setVpnGatewayId(final long vpnGatewayId) {
        this.vpnGatewayId = vpnGatewayId;
    }

    @Override
    public long getCustomerGatewayId() {
        return customerGatewayId;
    }

    @Override
    public State getState() {
        return state;
    }

    public void setState(final State state) {
        this.state = state;
    }

    @Override
    public Date getCreated() {
        return created;
    }

    public void setCreated(final Date created) {
        this.created = created;
    }

    @Override
    public Date getRemoved() {
        return removed;
    }

    public void setRemoved(final Date removed) {
        this.removed = removed;
    }

    @Override
    public boolean isPassive() {
        return passive;
    }

    public void setPassive(final boolean passive) {
        this.passive = passive;
    }

    @Override
    public boolean isDisplay() {
        return display;
    }

    public void setDisplay(final boolean display) {
        this.display = display;
    }

    public void setCustomerGatewayId(final long customerGatewayId) {
        this.customerGatewayId = customerGatewayId;
    }

    public void setUuid(final String uuid) {
        this.uuid = uuid;
    }

    @Override
    public long getDomainId() {
        return domainId;
    }

    @Override
    public long getAccountId() {
        return accountId;
    }

    @Override
    public Class<?> getEntityType() {
        return Site2SiteVpnConnection.class;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof Site2SiteVpnConnectionVO)) {
            return false;
        }

        final Site2SiteVpnConnectionVO that = (Site2SiteVpnConnectionVO) o;
        return getId() == that.getId();
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId());
    }
}
