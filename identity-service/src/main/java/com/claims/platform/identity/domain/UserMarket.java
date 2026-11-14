package com.claims.platform.identity.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Objects;

@Getter
@Setter
@Entity
@Table(name = "user_market")
@IdClass(UserMarket.Key.class)
public class UserMarket {
    @Id
    private String userId;
    @Id
    private String marketCode;

    public static class Key implements Serializable {
        private String userId;
        private String marketCode;

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public String getMarketCode() {
            return marketCode;
        }

        public void setMarketCode(String marketCode) {
            this.marketCode = marketCode;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Key key)) {
                return false;
            }
            return Objects.equals(userId, key.userId) && Objects.equals(marketCode, key.marketCode);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, marketCode);
        }
    }
}
