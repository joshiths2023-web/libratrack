package com.libratrack.service;

import com.libratrack.dao.MemberDao;
import com.libratrack.dao.TransactionDao;
import com.libratrack.model.Member;
import com.libratrack.model.MembershipTier;

import java.util.List;

/** Member registration, tier changes and borrowing-limit checks. */
public class MembershipService {
    private final MemberDao members;
    private final TransactionDao transactions;

    public MembershipService(MemberDao members, TransactionDao transactions) {
        this.members = members;
        this.transactions = transactions;
    }

    public Member register(String name, MembershipTier tier) {
        if (name == null || name.isBlank()) {
            throw new LibraryException("Name is required");
        }
        Member member = new Member(members.nextId(), name.trim(), tier);
        members.save(member);
        return member;
    }

    public Member changeTier(long memberId, MembershipTier tier) {
        Member member = find(memberId);
        member.setTier(tier);
        members.save(member);
        return member;
    }

    public Member find(long memberId) {
        return members.findById(memberId).orElseThrow(() -> new LibraryException("Member not found"));
    }

    public List<Member> all() {
        return members.findAll();
    }

    public int activeLoanCount(long memberId) {
        return transactions.findActiveByMember(memberId).size();
    }

    public boolean canBorrowMore(Member member) {
        return activeLoanCount(member.getId()) < member.getTier().getMaxBooks();
    }
}
