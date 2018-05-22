package com.monke.monkeybook.model.content;

import com.hwangjr.rxbus.RxBus;
import com.monke.basemvplib.BaseModelImpl;
import com.monke.monkeybook.bean.BookContentBean;
import com.monke.monkeybook.bean.BookInfoBean;
import com.monke.monkeybook.bean.BookShelfBean;
import com.monke.monkeybook.bean.BookSourceBean;
import com.monke.monkeybook.bean.ChapterListBean;
import com.monke.monkeybook.bean.SearchBookBean;
import com.monke.monkeybook.bean.WebChapterBean;
import com.monke.monkeybook.dao.BookSourceBeanDao;
import com.monke.monkeybook.dao.ChapterListBeanDao;
import com.monke.monkeybook.dao.DbHelper;
import com.monke.monkeybook.help.FormatWebText;
import com.monke.monkeybook.help.RxBusTag;
import com.monke.monkeybook.model.AnalyzeRule.AnalyzeElement;
import com.monke.monkeybook.model.AnalyzeRule.AnalyzeHeaders;
import com.monke.monkeybook.model.AnalyzeRule.AnalyzeSearchUrl;
import com.monke.monkeybook.model.ErrorAnalyContentManager;
import com.monke.monkeybook.model.impl.IHttpGetApi;
import com.monke.monkeybook.model.impl.IHttpPostApi;
import com.monke.monkeybook.model.impl.IStationBookModel;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import io.reactivex.Observable;
import retrofit2.Response;

import static android.text.TextUtils.isEmpty;

/**
 * 默认检索规则
 */
public class DefaultModelImpl extends BaseModelImpl implements IStationBookModel {
    private String TAG;
    private String name;
    private BookSourceBean bookSourceBean;
    private Map<String, String> headerMap = AnalyzeHeaders.getMap(null);

    private DefaultModelImpl(String tag) {
        TAG = tag;
        try {
            URL url = new URL(tag);
            name = url.getHost();
        } catch (MalformedURLException e) {
            e.printStackTrace();
            name = tag;
        }
    }

    public static DefaultModelImpl getInstance(String tag) {
        return new DefaultModelImpl(tag);
    }

    private Boolean initBookSourceBean() {
        if (bookSourceBean == null) {
            List<BookSourceBean> bookSourceBeans = DbHelper.getInstance().getmDaoSession().getBookSourceBeanDao().queryBuilder()
                    .where(BookSourceBeanDao.Properties.BookSourceUrl.eq(TAG)).build().list();
            if (bookSourceBeans != null && bookSourceBeans.size() > 0) {
                bookSourceBean = bookSourceBeans.get(0);
                name = bookSourceBean.getBookSourceName();
                headerMap = AnalyzeHeaders.getMap(bookSourceBean.getHttpUserAgent());
                return true;
            } else {
                return false;
            }
        } else {
            return true;
        }
    }

    /**
     * 发现
     */
    @Override
    public Observable<List<SearchBookBean>> findBook(String url, int page) {
        if (!initBookSourceBean() || isEmpty(bookSourceBean.getRuleSearchUrl())) {
            return Observable.create(emitter -> {
                emitter.onNext(new ArrayList<>());
                emitter.onComplete();
            });
        }
        try {
            AnalyzeSearchUrl analyzeSearchUrl = new AnalyzeSearchUrl(url, "", page);
            if (analyzeSearchUrl.getSearchUrl() == null) {
                return Observable.create(emitter -> {
                    emitter.onNext(new ArrayList<>());
                    emitter.onComplete();
                });
            }
            if (url.contains("@")) {
                return getRetrofitString(analyzeSearchUrl.getSearchUrl())
                        .create(IHttpPostApi.class)
                        .searchBook(analyzeSearchUrl.getSearchPath(),
                                analyzeSearchUrl.getQueryMap(),
                                headerMap)
                        .flatMap(this::analyzeSearchBook);
            } else if (url.contains("?")) {
                return getRetrofitString(analyzeSearchUrl.getSearchUrl())
                        .create(IHttpGetApi.class)
                        .searchBook(analyzeSearchUrl.getSearchPath(),
                                analyzeSearchUrl.getQueryMap(),
                                headerMap)
                        .flatMap(this::analyzeSearchBook);
            } else {
                return getRetrofitString(analyzeSearchUrl.getSearchUrl())
                        .create(IHttpGetApi.class)
                        .getWebContent(analyzeSearchUrl.getSearchPath(),
                                headerMap)
                        .flatMap(this::analyzeSearchBook);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return Observable.create(emitter -> {
                emitter.onNext(new ArrayList<>());
                emitter.onComplete();
            });
        }
    }

    /**
     * 搜索
     */
    @Override
    public Observable<List<SearchBookBean>> searchBook(String content, int page) {
        if (!initBookSourceBean() || isEmpty(bookSourceBean.getRuleSearchUrl())) {
            return Observable.create(emitter -> {
                emitter.onNext(new ArrayList<>());
                emitter.onComplete();
            });
        }
        try {
            AnalyzeSearchUrl analyzeSearchUrl = new AnalyzeSearchUrl(bookSourceBean.getRuleSearchUrl(), content, page);
            if (analyzeSearchUrl.getSearchUrl() == null) {
                return Observable.create(emitter -> {
                    emitter.onNext(new ArrayList<>());
                    emitter.onComplete();
                });
            }
            if (bookSourceBean.getRuleSearchUrl().contains("@")) {
                return getRetrofitString(analyzeSearchUrl.getSearchUrl())
                        .create(IHttpPostApi.class)
                        .searchBook(analyzeSearchUrl.getSearchPath(),
                                analyzeSearchUrl.getQueryMap(),
                                headerMap)
                        .flatMap(this::analyzeSearchBook);
            } else if (bookSourceBean.getRuleSearchUrl().contains("?")) {
                return getRetrofitString(analyzeSearchUrl.getSearchUrl())
                        .create(IHttpGetApi.class)
                        .searchBook(analyzeSearchUrl.getSearchPath(),
                                analyzeSearchUrl.getQueryMap(),
                                headerMap)
                        .flatMap(this::analyzeSearchBook);
            } else {
                return getRetrofitString(analyzeSearchUrl.getSearchUrl())
                        .create(IHttpGetApi.class)
                        .getWebContent(analyzeSearchUrl.getSearchPath(),
                                headerMap)
                        .flatMap(this::analyzeSearchBook);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return Observable.create(emitter -> {
                emitter.onNext(new ArrayList<>());
                emitter.onComplete();
            });
        }
    }

    private Observable<List<SearchBookBean>> analyzeSearchBook(final Response<String> response) {
        return Observable.create(e -> {
            try {
                String baseURI;
                okhttp3.Response networkResponse = response.raw().networkResponse();
                if (networkResponse != null && networkResponse.request() != null) {
                    baseURI = networkResponse.request().url().toString();
                } else {
                    baseURI = response.raw().request().url().toString();
                }
                Document doc = Jsoup.parse(response.body());
                Elements booksE = AnalyzeElement.getElements(doc, bookSourceBean.getRuleSearchList());
                if (null != booksE && booksE.size() > 0) {
                    List<SearchBookBean> books = new ArrayList<>();
                    for (int i = 0; i < booksE.size(); i++) {
                        SearchBookBean item = new SearchBookBean();
                        item.setTag(TAG);
                        item.setOrigin(name);
                        AnalyzeElement analyzeElement = new AnalyzeElement(booksE.get(i), baseURI);
                        item.setAuthor(FormatWebText.getAuthor(analyzeElement.getResult(bookSourceBean.getRuleSearchAuthor())));
                        item.setKind(analyzeElement.getResult(bookSourceBean.getRuleSearchKind()));
                        item.setLastChapter(analyzeElement.getResult(bookSourceBean.getRuleSearchLastChapter()));
                        item.setName(analyzeElement.getResult(bookSourceBean.getRuleSearchName()));
                        item.setNoteUrl(analyzeElement.getResult(bookSourceBean.getRuleSearchNoteUrl()));
                        if (isEmpty(item.getNoteUrl())) {
                            item.setNoteUrl(baseURI);
                        }
                        item.setCoverUrl(analyzeElement.getResult(bookSourceBean.getRuleSearchCoverUrl()));
                        if (!isEmpty(item.getName())) {
                            books.add(item);
                        }
                    }
                    e.onNext(books);
                } else {
                    e.onNext(new ArrayList<>());
                }
            } catch (Exception ex) {
                ex.printStackTrace();
                e.onNext(new ArrayList<>());
            }
            e.onComplete();
        });
    }

    /**
     * 获取书籍信息
     */
    @Override
    public Observable<BookShelfBean> getBookInfo(final BookShelfBean bookShelfBean) {
        if (!initBookSourceBean()) {
            return Observable.error(new Throwable(String.format("无法找到源%s", TAG)));
        }
        return getRetrofitString(TAG)
                .create(IHttpGetApi.class)
                .getWebContent(bookShelfBean.getNoteUrl(), headerMap)
                .flatMap(response -> analyzeBookInfo(response.body(), bookShelfBean));
    }

    private Observable<BookShelfBean> analyzeBookInfo(String s, final BookShelfBean bookShelfBean) {
        return Observable.create(e -> {
            bookShelfBean.setTag(TAG);
            BookInfoBean bookInfoBean = bookShelfBean.getBookInfoBean();
            if (bookInfoBean == null) {
                bookInfoBean = new BookInfoBean();
            }
            bookInfoBean.setNoteUrl(bookShelfBean.getNoteUrl());   //id
            bookInfoBean.setTag(TAG);
            Document doc = Jsoup.parse(s);
            AnalyzeElement analyzeElement = new AnalyzeElement(doc, bookShelfBean.getNoteUrl());
            if (isEmpty(bookInfoBean.getCoverUrl())) {
                bookInfoBean.setCoverUrl(analyzeElement.getResult(bookSourceBean.getRuleCoverUrl()));
            }
            if (isEmpty(bookInfoBean.getName())) {
                bookInfoBean.setName(analyzeElement.getResult(bookSourceBean.getRuleBookName()));
            }
            if (isEmpty(bookInfoBean.getAuthor())) {
                bookInfoBean.setAuthor(FormatWebText.getAuthor(analyzeElement.getResult(bookSourceBean.getRuleBookAuthor())));
            }
            bookInfoBean.setIntroduce(analyzeElement.getResult(bookSourceBean.getRuleIntroduce()));
            String chapterUrl = analyzeElement.getResult(bookSourceBean.getRuleChapterUrl());
            if (isEmpty(chapterUrl)) {
                bookInfoBean.setChapterUrl(bookShelfBean.getNoteUrl());
            } else {
                bookInfoBean.setChapterUrl(chapterUrl);
            }
            bookInfoBean.setOrigin(name);
            bookShelfBean.setBookInfoBean(bookInfoBean);
            e.onNext(bookShelfBean);
            e.onComplete();
        });
    }

    /**
     * 获取目录
     */
    @Override
    public Observable<BookShelfBean> getChapterList(final BookShelfBean bookShelfBean) {
        if (!initBookSourceBean()) {
            return Observable.create(emitter -> {
                bookShelfBean.setErrorMsg(String.format("%s没有找到书源配置", bookShelfBean.getBookInfoBean().getName()));
                emitter.onNext(bookShelfBean);
                emitter.onComplete();
            });
        }
        return getRetrofitString(TAG)
                .create(IHttpGetApi.class)
                .getWebContent(bookShelfBean.getBookInfoBean().getChapterUrl(), headerMap)
                .flatMap(response -> analyzeChapterList(response.body(), bookShelfBean));
    }

    private Observable<BookShelfBean> analyzeChapterList(final String s, final BookShelfBean bookShelfBean) {
        return Observable.create(e -> {
            bookShelfBean.setTag(TAG);
            WebChapterBean<List<ChapterListBean>> chapterList = analyzeChapterList(s, bookShelfBean.getNoteUrl(), bookShelfBean.getBookInfoBean().getChapterUrl(), bookShelfBean.getChapterList());
            if (bookShelfBean.getChapterListSize() < chapterList.getData().size()) {
                bookShelfBean.setHasUpdate(true);
                bookShelfBean.setFinalRefreshData(System.currentTimeMillis());
                bookShelfBean.getBookInfoBean().setFinalRefreshData(System.currentTimeMillis());
            }
            bookShelfBean.getBookInfoBean().setChapterList(chapterList.getData());
            e.onNext(bookShelfBean);
            e.onComplete();
        });
    }

    private WebChapterBean<List<ChapterListBean>> analyzeChapterList(String s, String novelUrl, String chapterUrl, List<ChapterListBean> chapterListBeansOld) {
        Document doc = Jsoup.parse(s);
        boolean dx = false;
        String ruleChapterList = bookSourceBean.getRuleChapterList();
        if (ruleChapterList != null && ruleChapterList.startsWith("-")) {
            dx = true;
            ruleChapterList = ruleChapterList.substring(1);
        }
        Elements chapterList = AnalyzeElement.getElements(doc, ruleChapterList);
        List<ChapterListBean> chapterBeans = new ArrayList<>();
        int x;
        for (int i = 0; i < chapterList.size(); i++) {
            AnalyzeElement analyzeElement = new AnalyzeElement(chapterList.get(i), chapterUrl);
            ChapterListBean temp = new ChapterListBean();
            temp.setDurChapterIndex(i);
            temp.setDurChapterUrl(analyzeElement.getResult(bookSourceBean.getRuleContentUrl()));   //id
            temp.setDurChapterName(analyzeElement.getResult(bookSourceBean.getRuleChapterName()));
            temp.setNoteUrl(novelUrl);
            temp.setTag(TAG);
            if (!isEmpty(temp.getDurChapterUrl()) && !isEmpty(temp.getDurChapterName())) {
                x = chapterListBeansOld.indexOf(temp);
                if (x != -1) {
                    temp.setHasCache(chapterListBeansOld.get(x).getHasCache());
                }
                chapterBeans.add(temp);
            }
        }
        if (dx) {
            Collections.reverse(chapterBeans);
        }
        return new WebChapterBean<>(chapterBeans, false);
    }

    /**
     * 获取正文
     */
    @Override
    public Observable<BookContentBean> getBookContent(final String durChapterUrl, final int durChapterIndex) {
        if (!initBookSourceBean()) {
            return Observable.create(emitter -> {
                emitter.onNext(new BookContentBean());
                emitter.onComplete();
            });
        }
        return getRetrofitString(TAG)
                .create(IHttpGetApi.class)
                .getWebContent(durChapterUrl, headerMap)
                .flatMap(response -> analyzeBookContent(response.body(), durChapterUrl, durChapterIndex))
                .flatMap(this::upChapterList);
    }

    private Observable<BookContentBean> analyzeBookContent(final String s, final String durChapterUrl, final int durChapterIndex) {
        return Observable.create(e -> {
            BookContentBean bookContentBean = new BookContentBean();
            bookContentBean.setDurChapterIndex(durChapterIndex);
            bookContentBean.setDurChapterUrl(durChapterUrl);
            bookContentBean.setTag(TAG);
            try {
                Document doc = Jsoup.parse(s);
                AnalyzeElement analyzeElement = new AnalyzeElement(doc, durChapterUrl);
                bookContentBean.setDurChapterContent(analyzeElement.getResult(bookSourceBean.getRuleBookContent()));
                bookContentBean.setRight(true);
            } catch (Exception ex) {
                ex.printStackTrace();
                ErrorAnalyContentManager.getInstance().writeNewErrorUrl(durChapterUrl);
                bookContentBean.setDurChapterContent(durChapterUrl.substring(0, durChapterUrl.indexOf('/', 8)) + ex.getMessage());
                bookContentBean.setRight(false);
            }
            e.onNext(bookContentBean);
            e.onComplete();
        });
    }

    private Observable<BookContentBean> upChapterList(BookContentBean bookContentBean) {
        return Observable.create(e -> {
            if (bookContentBean.getRight()) {
                DbHelper.getInstance().getmDaoSession().getBookContentBeanDao().insertOrReplaceInTx(bookContentBean);
                ChapterListBean chapterListBean = DbHelper.getInstance().getmDaoSession().getChapterListBeanDao().queryBuilder()
                        .where(ChapterListBeanDao.Properties.DurChapterUrl.eq(bookContentBean.getDurChapterUrl())).unique();
                if (chapterListBean != null) {
                    bookContentBean.setNoteUrl(chapterListBean.getNoteUrl());
                    chapterListBean.setHasCache(true);
                    DbHelper.getInstance().getmDaoSession().getChapterListBeanDao().update(chapterListBean);
                    RxBus.get().post(RxBusTag.CHAPTER_CHANGE, chapterListBean);
                }
            }
            e.onNext(bookContentBean);
            e.onComplete();
        });
    }

    /////////////////////////////////////////////////////////////////////////////////////////////////////////////
}
