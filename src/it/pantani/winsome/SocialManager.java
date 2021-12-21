/*
 * Copyright (c) 2021/2022
 * Leonardo Pantani - 598896
 * University of Pisa - Department of Computer Science
 */

package it.pantani.winsome;

import it.pantani.winsome.entities.WinSomeComment;
import it.pantani.winsome.entities.WinSomePost;
import it.pantani.winsome.entities.WinSomeUser;
import it.pantani.winsome.entities.WinSomeWallet;
import it.pantani.winsome.exceptions.*;
import it.pantani.winsome.utils.ConfigManager;
import it.pantani.winsome.utils.PostComparator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static it.pantani.winsome.utils.Utils.getFormattedDate;

public class SocialManager {
    private final ConfigManager config;

    private final AtomicInteger lastID;

    private ConcurrentHashMap<String, WinSomeUser> userList;
    private ConcurrentHashMap<String, ArrayList<String>> followersList;
    private ConcurrentHashMap<String, ArrayList<String>> followingList; // ridondanza
    private ConcurrentHashMap<Integer, WinSomePost> postList;
    private ConcurrentHashMap<String, WinSomeWallet> walletList;

    public SocialManager(ConfigManager config) throws IOException {
        this.config = config;
        userList = new ConcurrentHashMap<>();
        followersList = new ConcurrentHashMap<>();
        followingList = new ConcurrentHashMap<>();
        walletList = new ConcurrentHashMap<>();
        postList = new ConcurrentHashMap<>();

        // elaborazione last_post_id
        String id_toParse = config.getPreference("last_post_id");
        if(id_toParse == null) { // caricamento config fallito
            throw new IOException("id proprieta' nullo");
        }
        int id_parsed;
        try {
            id_parsed = Integer.parseInt(id_toParse);
        } catch(NumberFormatException e) {
            throw new IOException("numero non valido");
        }
        if(id_parsed < 0) throw new IOException("numero negativo");
        lastID = new AtomicInteger(id_parsed);
    }

    public void close(ConfigManager config) {
        config.saveLastPostID(lastID.intValue());
    }

    public int createPost(String username, String post_title, String post_content) throws PostTitleTooLongException, PostContentTooLongException, UserNotFoundException {
        if(!userList.containsKey(username)) throw new UserNotFoundException();
        if(post_title.length() > Integer.parseInt(config.getPreference("post_max_title_length"))) throw new PostTitleTooLongException();
        if(post_content.length() > Integer.parseInt(config.getPreference("post_max_content_length"))) throw new PostContentTooLongException();

        int idpost = lastID.getAndIncrement();
        postList.putIfAbsent(idpost, new WinSomePost(idpost, username, post_title, post_content));
        return idpost;
    }

    public void ratePost(String username, int post_id, int value) throws UserNotFoundException, InvalidVoteException, PostNotFoundException, InvalidOperationException, SameAuthorException, NotInFeedException {
        if(!userList.containsKey(username)) throw new UserNotFoundException();
        if(value != -1 && value != 1) throw new InvalidVoteException();
        WinSomePost toRate = postList.get(post_id);
        if(toRate == null) throw new PostNotFoundException();
        if(toRate.getAuthor().equals(username)) throw new SameAuthorException();
        if(!isPostInFeed(post_id, username)) throw new NotInFeedException();
        if(toRate.findVoteByUser(username) != null) throw new InvalidOperationException();

        toRate.addVote(username, value);
    }

    public void commentPost(String username, int post_id, String text) throws PostNotFoundException, SameAuthorException, NotInFeedException {
        WinSomePost toComment = postList.get(post_id);
        if(toComment == null) throw new PostNotFoundException();
        if(toComment.getAuthor().equals(username)) throw new SameAuthorException();
        if(!isPostInFeed(post_id, username)) throw new NotInFeedException();

        toComment.addComment(username, text);
    }

    public void deletePost(String username, int post_id) throws PostNotFoundException, InvalidOperationException {
        WinSomePost toDelete = postList.get(post_id);
        if(toDelete == null) throw new PostNotFoundException();
        if(!toDelete.getAuthor().equals(username)) throw new InvalidOperationException();

        postList.remove(post_id);
    }

    public WinSomePost getPost(int post_id) {
        return postList.get(post_id);
    }

    @SuppressWarnings("StringConcatenationInLoop")
    public String getPostFormatted(int post_id, boolean hideAuthor) {
        WinSomePost p = postList.get(post_id);
        String ret;
        if(p == null) return null;
        ArrayList<WinSomeComment> lista_commenti = p.getComments();

        ret = "[Post #" + p.getPostID() + "]\n";
        ret += "Titolo: " + p.getPostTitle() + "\n";
        ret += "Contenuto: " + p.getPostContent() + "\n";
        if(!hideAuthor) ret += "Autore: " + p.getAuthor() + "\n";
        ret += "Voti: " + p.getUpvotes() + " ";
        if(p.getUpvotes() == 1) ret += "positivo"; else ret += "positivi";
        ret += ", " + p.getDownvotes() + " ";
        if(p.getDownvotes() == 1) ret += "negativo"; else ret += "negativi";
        ret += "\n";
        ret += "Data: " + getFormattedDate(p.getDateSent()) + "\n";
        if(lista_commenti != null) {
            ret += "- Commenti (" + lista_commenti.size() + "):\n";
            for(WinSomeComment c : lista_commenti) {
                ret += "- " + c.getAuthor() + ": " + c.getContent() + "\n";
            }
        }
        return ret;
    }

    public ArrayList<WinSomeUser> getUsersWithSimilarTags(Set<String> tags_list) {
        ArrayList<WinSomeUser> usersWithTag = null;
        ArrayList<WinSomeUser> ret;

        for(String t : tags_list) {
            ret = getUsersByTag(t);
            for(WinSomeUser u : ret) {
                if(usersWithTag == null) usersWithTag = new ArrayList<>();
                if(!usersWithTag.contains(u)) {
                    usersWithTag.add(u);
                }
            }
        }

        return usersWithTag;
    }

    public ArrayList<WinSomeUser> getUsersByTag(String tag) {
        ArrayList<WinSomeUser> ret = null;
        for(WinSomeUser u : userList.values()) {
            if(u.getTags_list().contains(tag)) {
                if(ret == null) ret = new ArrayList<>();
                ret.add(u);
            }
        }

        return ret;
    }

    public ArrayList<WinSomePost> getUserPosts(String username) {
        ArrayList<WinSomePost> ret = null;

        for (WinSomePost p : postList.values()) {
            if(p.getAuthor().equals(username)) {
                if(ret == null) ret = new ArrayList<>();
                ret.add(p);
            }
        }

        return ret;
    }

    public ArrayList<WinSomePost> getUserFeed(String username) {
        ArrayList<String> usersFollowedByUser = getFollowing(username);
        if(usersFollowedByUser == null) {
            return null;
        }

        ArrayList<WinSomePost> feed = new ArrayList<>();
        for(String u : usersFollowedByUser) {
            ArrayList<WinSomePost> p = getUserPosts(u);
            if(p != null) feed.addAll(p);
        }
        feed.sort(new PostComparator().reversed()); // ordino per data decrescente i post nel blog

        return feed;
    }

    boolean isPostInFeed(int post_id, String username) {
        ArrayList<WinSomePost> user_feed = getUserFeed(username);
        for(WinSomePost p : user_feed) {
            if(p.getPostID() == post_id) {
                return true;
            }
        }
        return false;
    }

    public void setPostList(ConcurrentHashMap<Integer, WinSomePost> postList) {
        this.postList = postList;
    }

    public void setUserList(ConcurrentHashMap<String, WinSomeUser> userList) {
        this.userList = userList;
    }

    public void setFollowersList(ConcurrentHashMap<String, ArrayList<String>> followersList) {
        this.followersList = followersList;
    }

    public void setFollowingList(ConcurrentHashMap<String, ArrayList<String>> followingList) {
        this.followingList = followingList;
    }

    public ConcurrentHashMap<Integer, WinSomePost> getPostList() {
        return postList;
    }

    public ConcurrentHashMap<String, ArrayList<String>> getFollowersList() {
        return followersList;
    }

    public ConcurrentHashMap<String, ArrayList<String>> getFollowingList() {
        return followingList;
    }

    public ConcurrentHashMap<String, WinSomeUser> getUserList() {
        return userList;
    }

    public void addUser(WinSomeUser user) {
        userList.put(user.getUsername(), user);
    }

    public void addFollower(String username, String follower) {
        if (followersList.containsKey(username)) {
            followersList.get(username).add(follower);
        } else {
            ArrayList<String> list = new ArrayList<>();
            list.add(follower);
            followersList.put(username, list);
        }
    }

    public void addFollowing(String username, String following) {
        if (followingList.containsKey(username)) {
            followingList.get(username).add(following);
        } else {
            ArrayList<String> list = new ArrayList<>();
            list.add(following);
            followingList.put(username, list);
        }
    }

    public WinSomeUser getUser(String username) {
        return userList.get(username);
    }

    public ArrayList<String> getFollowers(String username) {
        return followersList.get(username);
    }

    public ArrayList<String> getFollowing(String username) {
        return followingList.get(username);
    }

    public void removeUser(String username) {
        userList.remove(username);
        followersList.remove(username);
        followingList.remove(username);
    }

    public void removeFollower(String username, String follower) {
        followersList.get(username).remove(follower);
    }

    public void removeFollowing(String username, String following) {
        followingList.get(username).remove(following);
    }

    public boolean findUser(String username) {
        return userList.containsKey(username);
    }

    public boolean findFollower(String username, String follower) {
        return followersList.get(username).contains(follower);
    }

    public boolean findFollowing(String username, String following) {
        return followingList.get(username).contains(following);
    }

    public int getUserCount() {
        return userList.size();
    }

    public WinSomeWallet getWalletByUsername(String username) {
        walletList.putIfAbsent(username, new WinSomeWallet(username));
        return walletList.get(username);
    }

    public ConcurrentHashMap<String, WinSomeWallet> getWalletList() {
        return walletList;
    }

    public void setWalletList(ConcurrentHashMap<String, WinSomeWallet> walletList) {
        this.walletList = walletList;
    }

    public int getFollowerCount(String username) {
        return followersList.get(username).size();
    }

    public int getFollowingCount(String username) {
        return followingList.get(username).size();
    }
}
