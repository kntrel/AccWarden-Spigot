package com.kntrel.mc.accwarden.account;

import com.kntrel.mc.accwarden.io.LangProvider;
import com.kntrel.mc.accwarden.io.database.DataBase;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class AccountRepository {

    //FIELDS
    private final DataBase dataBase_;
    private int minLength_ = 0, maxLength_ = 12;
    private LangProvider langProvider_ = null;

    //CONSTRUCTOR
    public AccountRepository(DataBase dataBase) {
        this.dataBase_ = dataBase;
    }

    //SETTERS
    public void setSizes(int min, int max) {
        this.minLength_ = min; this.maxLength_ = max;
    }
    public void setLangProvider(LangProvider langProvider) {
        this.langProvider_ = langProvider;
    }

    //GETTERS
    public DataBase getDataBase() {
        return this.dataBase_;
    }

    //METHODS
    public Account retrieve(Player player) {
        Optional<Account> optional = this.dataBase_.get(Account.class, player.getUniqueId());
        Account acc;
        if (optional.isEmpty()) {
            acc = new Account(player.getUniqueId(), player.getName(),this);
            acc.setSizes(this.minLength_, this.maxLength_);
            return acc;
        }
        acc = optional.get();
        acc.setRepository(this);
        acc.setSizes(this.minLength_,this.maxLength_);
        return acc;
    }
    public Optional<Account> get(UUID id) {
        return this.dataBase_.get(Account.class, id);
    }
    public Set<Account> getByName(String name) {
        return this.dataBase_.query(Account.class,"name", name);
    }
    public boolean exists(Player player) {
        return this.exists(player.getUniqueId());
    }
    public boolean exists(UUID id) {
        return this.dataBase_.get(Account.class, id).isPresent();
    }
    public void save (Account toSave) {
        this.dataBase_.write(toSave);
    }
    public void delete(Account toDelete) {
        this.dataBase_.delete(toDelete);
        Player affected = Bukkit.getPlayer(toDelete.getId());
        if (affected == null) { return; }
        String message = (this.langProvider_ == null) ? "" : this.langProvider_.getEntry(affected,"info.account_deleted");
        affected.kickPlayer(message);
    }

}
