/*
 * SonarQube, open source software quality management tool.
 * Copyright (C) 2008-2013 SonarSource
 * mailto:contact AT sonarsource DOT com
 *
 * SonarQube is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * SonarQube is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package org.sonar.server.issue;

import com.google.common.collect.Lists;
import org.apache.commons.lang.ObjectUtils;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.sonar.api.issue.IssueFinder;
import org.sonar.api.issue.IssueQuery;
import org.sonar.api.web.UserRole;
import org.sonar.core.issue.DefaultIssueFilter;
import org.sonar.core.issue.IssueFilterSerializer;
import org.sonar.core.issue.db.IssueFilterDao;
import org.sonar.core.issue.db.IssueFilterDto;
import org.sonar.core.issue.db.IssueFilterFavouriteDao;
import org.sonar.core.issue.db.IssueFilterFavouriteDto;
import org.sonar.core.permission.GlobalPermissions;
import org.sonar.core.user.AuthorizationDao;
import org.sonar.server.exceptions.BadRequestException;
import org.sonar.server.exceptions.ForbiddenException;
import org.sonar.server.exceptions.NotFoundException;
import org.sonar.server.exceptions.UnauthorizedException;
import org.sonar.server.user.MockUserSession;
import org.sonar.server.user.UserSession;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Maps.newHashMap;
import static org.fest.assertions.Assertions.assertThat;
import static org.fest.assertions.Fail.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyMap;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

public class IssueFilterServiceTest {

  private IssueFilterService service;

  private IssueFilterDao issueFilterDao;
  private IssueFilterFavouriteDao issueFilterFavouriteDao;
  private IssueFinder issueFinder;
  private AuthorizationDao authorizationDao;
  private IssueFilterSerializer issueFilterSerializer;

  private UserSession userSession;

  @Before
  public void before() {
    userSession = MockUserSession.create().setLogin("john");

    issueFilterDao = mock(IssueFilterDao.class);
    issueFilterFavouriteDao = mock(IssueFilterFavouriteDao.class);
    issueFinder = mock(IssueFinder.class);
    authorizationDao = mock(AuthorizationDao.class);
    issueFilterSerializer = mock(IssueFilterSerializer.class);

    service = new IssueFilterService(issueFilterDao, issueFilterFavouriteDao, issueFinder, authorizationDao, issueFilterSerializer);
  }

  @Test
  public void should_find_by_id() {
    IssueFilterDto issueFilterDto = new IssueFilterDto().setId(1L).setName("My Issue").setUserLogin("john");
    when(issueFilterDao.selectById(1L)).thenReturn(issueFilterDto);

    DefaultIssueFilter issueFilter = service.findById(1L);
    assertThat(issueFilter).isNotNull();
    assertThat(issueFilter.id()).isEqualTo(1L);
  }

  @Test
  public void should_find_issue_filter() {
    IssueFilterDto issueFilterDto = new IssueFilterDto().setId(1L).setName("My Issue").setUserLogin("john");
    when(issueFilterDao.selectById(1L)).thenReturn(issueFilterDto);

    DefaultIssueFilter issueFilter = service.find(1L, userSession);
    assertThat(issueFilter).isNotNull();
    assertThat(issueFilter.id()).isEqualTo(1L);
  }

  @Test
  public void should_find_shared_filter_by_id() {
    IssueFilterDto issueFilterDto = new IssueFilterDto().setId(1L).setName("My Issue").setUserLogin("arthur").setShared(true);
    when(issueFilterDao.selectById(1L)).thenReturn(issueFilterDto);

    DefaultIssueFilter issueFilter = service.find(1L, userSession);
    assertThat(issueFilter).isNotNull();
    assertThat(issueFilter.id()).isEqualTo(1L);
  }

  @Test
  public void should_not_find_by_id_on_not_existing_issue() {
    when(issueFilterDao.selectById(1L)).thenReturn(null);
    try {
      service.find(1L, userSession);
      fail();
    } catch (Exception e) {
      assertThat(e).isInstanceOf(NotFoundException.class).hasMessage("Filter not found: 1");
    }
  }

  @Test
  public void should_not_find_by_id_if_not_logged() {
    UserSession userSession = MockUserSession.create().setLogin(null);
    try {
      service.find(1L, userSession);
      fail();
    } catch (Exception e) {
      assertThat(e).isInstanceOf(UnauthorizedException.class).hasMessage("User is not logged in");
    }
    verifyZeroInteractions(issueFilterDao);
  }

  @Test
  public void should_not_find_if_not_shared_and_user_is_not_the_owner_of_filter() {
    IssueFilterDto issueFilterDto = new IssueFilterDto().setId(1L).setName("My Issue").setUserLogin("eric").setShared(false);
    when(issueFilterDao.selectById(1L)).thenReturn(issueFilterDto);
    try {
      // John is not authorized to see eric filters
      service.find(1L, userSession);
      fail();
    } catch (Exception e) {
      assertThat(e).isInstanceOf(ForbiddenException.class).hasMessage("User is not authorized to read this filter");
    }
  }

  @Test
  public void should_find_by_user() {
    when(issueFilterDao.selectByUser("john")).thenReturn(newArrayList(new IssueFilterDto().setId(1L).setName("My Issue").setUserLogin("john")));

    List<DefaultIssueFilter> issueFilter = service.findByUser(userSession);
    assertThat(issueFilter).hasSize(1);
    assertThat(issueFilter.get(0).id()).isEqualTo(1L);
  }

  @Test
  public void should_not_find_by_user_if_not_logged() {
    UserSession userSession = MockUserSession.create().setLogin(null);
    try {
      service.findByUser(userSession);
      fail();
    } catch (Exception e) {
      assertThat(e).isInstanceOf(UnauthorizedException.class).hasMessage("User is not logged in");
    }
  }

  @Test
  public void should_save() {
    DefaultIssueFilter issueFilter = new DefaultIssueFilter().setName("My Issue");
    DefaultIssueFilter result = service.save(issueFilter, userSession);
    assertThat(result.name()).isEqualTo("My Issue");
    assertThat(result.user()).isEqualTo("john");

    verify(issueFilterDao).insert(any(IssueFilterDto.class));
  }

  @Test
  public void should_add_favorite_on_save() {
    DefaultIssueFilter issueFilter = new DefaultIssueFilter().setName("My Issue");
    service.save(issueFilter, userSession);

    verify(issueFilterDao).insert(any(IssueFilterDto.class));
    verify(issueFilterFavouriteDao).insert(any(IssueFilterFavouriteDto.class));
  }

  @Test
  public void should_not_save_if_not_logged() {
    UserSession userSession = MockUserSession.create().setLogin(null);
    try {
      DefaultIssueFilter issueFilter = new DefaultIssueFilter().setName("My Issue");
      service.save(issueFilter, userSession);
      fail();
    } catch (Exception e) {
      assertThat(e).isInstanceOf(UnauthorizedException.class).hasMessage("User is not logged in");
    }
    verify(issueFilterDao, never()).insert(any(IssueFilterDto.class));
  }

  @Test
  public void should_not_save_if_name_already_used() {
    when(issueFilterDao.selectByUser(eq("john"))).thenReturn(newArrayList(new IssueFilterDto().setId(1L).setName("My Issue")));
    try {
      DefaultIssueFilter issueFilter = new DefaultIssueFilter().setName("My Issue");
      service.save(issueFilter, userSession);
      fail();
    } catch (Exception e) {
      assertThat(e).isInstanceOf(BadRequestException.class).hasMessage("Name already exists");
    }
    verify(issueFilterDao, never()).insert(any(IssueFilterDto.class));
  }

  @Test
  public void should_not_save_shared_filter_if_name_already_used_by_shared_filter() {
    when(issueFilterDao.selectByUser(eq("john"))).thenReturn(Collections.<IssueFilterDto> emptyList());
    when(issueFilterDao.selectSharedFilters()).thenReturn(newArrayList(new IssueFilterDto().setId(1L).setName("My Issue").setUserLogin("henry").setShared(true)));
    DefaultIssueFilter issueFilter = new DefaultIssueFilter().setName("My Issue").setShared(true);
    try {
      service.save(issueFilter, userSession);
      fail();
    } catch (Exception e) {
      assertThat(e).isInstanceOf(BadRequestException.class).hasMessage("Other users already share filters with the same name");
    }
    verify(issueFilterDao, never()).insert(any(IssueFilterDto.class));
  }

  @Test
  public void should_update() {
    when(issueFilterDao.selectById(1L)).thenReturn(new IssueFilterDto().setId(1L).setName("My Old Filter").setUserLogin("john"));

    DefaultIssueFilter result = service.update(new DefaultIssueFilter().setId(1L).setName("My New Filter").setUser("john"), userSession);
    assertThat(result.name()).isEqualTo("My New Filter");

    verify(issueFilterDao).update(any(IssueFilterDto.class));
  }

  @Test
  public void should_have_permission_to_share_filter() {
    when(authorizationDao.selectGlobalPermissions("john")).thenReturn(newArrayList(GlobalPermissions.DASHBOARD_SHARING));
    when(issueFilterDao.selectById(1L)).thenReturn(new IssueFilterDto().setId(1L).setName("My Filter").setShared(false).setUserLogin("john"));

    DefaultIssueFilter result = service.update(new DefaultIssueFilter().setId(1L).setName("My Filter").setShared(true).setUser("john"), userSession);
    assertThat(result.shared()).isTrue();

    verify(issueFilterDao).update(any(IssueFilterDto.class));
  }

  @Test
  public void should_not_share_filter_if_no_permission() {
    when(authorizationDao.selectGlobalPermissions("john")).thenReturn(Collections.<String>emptyList());
    when(issueFilterDao.selectById(1L)).thenReturn(new IssueFilterDto().setId(1L).setName("My Filter").setShared(false).setUserLogin("john"));

    try {
      service.update(new DefaultIssueFilter().setId(1L).setName("My Filter").setShared(true).setUser("john"), userSession);
      fail();
    } catch (Exception e) {
      assertThat(e).isInstanceOf(ForbiddenException.class).hasMessage("User cannot own this filter because of insufficient rights");
    }
    verify(issueFilterDao, never()).update(any(IssueFilterDto.class));
  }

  @Test
  public void should_not_update_sharing_if_not_owner() {
    // John is admin and want to change arthur filter sharing
    when(authorizationDao.selectGlobalPermissions("john")).thenReturn(newArrayList(GlobalPermissions.SYSTEM_ADMIN));
    when(issueFilterDao.selectById(1L)).thenReturn(new IssueFilterDto().setId(1L).setName("Arthur Filter").setShared(true).setUserLogin("arthur"));

    try {
      service.update(new DefaultIssueFilter().setId(1L).setName("Arthur Filter").setShared(false).setUser("john"), userSession);
      fail();
    } catch (Exception e) {
      assertThat(e).isInstanceOf(ForbiddenException.class).hasMessage("Only owner of a filter can change sharing");
    }
    verify(issueFilterDao, never()).update(any(IssueFilterDto.class));
  }

  @Test
  public void should_update_own_user_filter_without_changing_anything() {
    IssueFilterDto dto = new IssueFilterDto().setId(1L).setName("My Filter").setUserLogin("john");
    when(issueFilterDao.selectById(1L)).thenReturn(dto);
    when(issueFilterDao.selectByUser("john")).thenReturn(newArrayList(dto));

    DefaultIssueFilter result = service.update(new DefaultIssueFilter().setId(1L).setName("My Filter").setUser("john"), userSession);
    assertThat(result.name()).isEqualTo("My Filter");

    verify(issueFilterDao).update(any(IssueFilterDto.class));
  }

  @Test
  public void should_remove_other_favorite_filters_if_filter_become_unshared() {
    when(issueFilterDao.selectById(1L)).thenReturn(new IssueFilterDto().setId(1L).setName("My Old Filter").setUserLogin("john").setShared(true));
    IssueFilterFavouriteDto userFavouriteDto = new IssueFilterFavouriteDto().setId(10L).setUserLogin("john").setIssueFilterId(1L);
    IssueFilterFavouriteDto otherFavouriteDto = new IssueFilterFavouriteDto().setId(11L).setUserLogin("arthur").setIssueFilterId(1L);
    when(issueFilterFavouriteDao.selectByFilterId(1L)).thenReturn(newArrayList(userFavouriteDto, otherFavouriteDto));

    DefaultIssueFilter result = service.update(new DefaultIssueFilter().setId(1L).setName("My New Filter").setUser("john").setShared(false), userSession);
    assertThat(result.name()).isEqualTo("My New Filter");

    verify(issueFilterDao).update(any(IssueFilterDto.class));
    verify(issueFilterFavouriteDao).delete(11L);
    verify(issueFilterFavouriteDao, never()).delete(10L);
  }

  @Test
  public void should_update_other_shared_filter_if_admin_and_if_filter_owner_has_sharing_permission() {
    when(authorizationDao.selectGlobalPermissions("john")).thenReturn(newArrayList(GlobalPermissions.SYSTEM_ADMIN));
    when(authorizationDao.selectGlobalPermissions("arthur")).thenReturn(newArrayList(GlobalPermissions.DASHBOARD_SHARING));
    when(issueFilterDao.selectById(1L)).thenReturn(new IssueFilterDto().setId(1L).setName("My Old Filter").setDescription("Old description").setUserLogin("arthur").setShared(true));

    DefaultIssueFilter result = service.update(new DefaultIssueFilter().setId(1L).setName("My New Filter").setDescription("New description").setShared(true).setUser("arthur"), userSession);
    assertThat(result.name()).isEqualTo("My New Filter");
    assertThat(result.description()).isEqualTo("New description");

    verify(issueFilterDao).update(any(IssueFilterDto.class));
  }

  @Test
  public void should_not_update_other_shared_filter_if_admin_and_if_filter_owner_has_no_sharing_permission() {
    when(authorizationDao.selectGlobalPermissions("john")).thenReturn(newArrayList(GlobalPermissions.SYSTEM_ADMIN));
    when(authorizationDao.selectGlobalPermissions("arthur")).thenReturn(Collections.<String>emptyList());
    when(issueFilterDao.selectById(1L)).thenReturn(new IssueFilterDto().setId(1L).setName("My Old Filter").setDescription("Old description").setUserLogin("arthur").setShared(true));

    try {
      service.update(new DefaultIssueFilter().setId(1L).setName("My New Filter").setDescription("New description").setShared(true).setUser("arthur"), userSession);
      fail();
    } catch (Exception e) {
      assertThat(e).isInstanceOf(ForbiddenException.class).hasMessage("User cannot own this filter because of insufficient rights");
    }
    verify(issueFilterDao, never()).update(any(IssueFilterDto.class));
  }

  @Test
  public void should_not_update_if_filter_not_found() {
    when(issueFilterDao.selectById(1L)).thenReturn(null);

    try {
      service.update(new DefaultIssueFilter().setId(1L).setName("My New Filter"), userSession);
      fail();
    } catch (Exception e) {
      assertThat(e).isInstanceOf(NotFoundException.class).hasMessage("Filter not found: 1");
    }
    verify(issueFilterDao, never()).update(any(IssueFilterDto.class));
  }

  @Test
  public void should_not_update_if_shared_and_not_admin() {
    when(authorizationDao.selectGlobalPermissions("john")).thenReturn(newArrayList(UserRole.USER));
    when(issueFilterDao.selectById(1L)).thenReturn(new IssueFilterDto().setId(1L).setName("My Old Filter").setUserLogin("arthur").setShared(true));

    try {
      service.update(new DefaultIssueFilter().setId(1L).setName("My New Filter"), userSession);
      fail();
    } catch (Exception e) {
      assertThat(e).isInstanceOf(ForbiddenException.class).hasMessage("User is not authorized to modify this filter");
    }
    verify(issueFilterDao, never()).update(any(IssueFilterDto.class));
  }

  @Test
  public void should_not_update_if_name_already_used() {
    when(issueFilterDao.selectById(1L)).thenReturn(new IssueFilterDto().setId(1L).setName("My Old Filter").setUserLogin("john"));
    when(issueFilterDao.selectByUser(eq("john"))).thenReturn(newArrayList(new IssueFilterDto().setId(2L).setName("My Issue")));

    try {
      service.update(new DefaultIssueFilter().setId(1L).setUser("john").setName("My Issue"), userSession);
      fail();
    } catch (Exception e) {
      assertThat(e).isInstanceOf(BadRequestException.class).hasMessage("Name already exists");
    }
    verify(issueFilterDao, never()).update(any(IssueFilterDto.class));
  }

  @Test
  public void should_update_data() {
    when(issueFilterSerializer.serialize(anyMap())).thenReturn("componentRoots=struts");
    when(issueFilterDao.selectById(1L)).thenReturn(new IssueFilterDto().setId(1L).setName("My Old Filter").setUserLogin("john"));

    Map<String, Object> data = newHashMap();
    data.put("componentRoots", "struts");

    DefaultIssueFilter result = service.updateFilterQuery(1L, data, userSession);
    assertThat(result.data()).isEqualTo("componentRoots=struts");

    verify(issueFilterDao).update(any(IssueFilterDto.class));
  }

  @Test
  public void should_change_shared_filter_ownership_when_admin() throws Exception {
    IssueFilterDto sharedFilter = new IssueFilterDto().setId(1L).setName("My filter").setUserLogin("former.owner").setShared(true);
    IssueFilterDto expectedDto = new IssueFilterDto().setName("My filter").setUserLogin("new.owner").setShared(true);

    // New owner should have sharing perm in order to own the filter
    when(authorizationDao.selectGlobalPermissions("new.owner")).thenReturn(newArrayList(GlobalPermissions.DASHBOARD_SHARING));
    when(authorizationDao.selectGlobalPermissions("john")).thenReturn(newArrayList(GlobalPermissions.SYSTEM_ADMIN));

    when(issueFilterDao.selectById(1L)).thenReturn(sharedFilter);
    when(issueFilterDao.selectSharedFilters()).thenReturn(Lists.newArrayList(sharedFilter));

    DefaultIssueFilter issueFilter = new DefaultIssueFilter().setId(1L).setName("My filter").setShared(true).setUser("new.owner");
    service.update(issueFilter, userSession);

    verify(issueFilterDao).update(argThat(Matches.filter(expectedDto)));
  }

  @Test
  public void should_deny_filter_ownership_change_when_not_admin() throws Exception {
    String currentUser = "dave.loper";
    IssueFilterDto sharedFilter = new IssueFilterDto().setId(1L).setName("My filter").setUserLogin(currentUser).setShared(true);

    when(authorizationDao.selectGlobalPermissions(currentUser)).thenReturn(newArrayList(GlobalPermissions.DRY_RUN_EXECUTION));
    when(issueFilterDao.selectById(1L)).thenReturn(sharedFilter);

    try {
      DefaultIssueFilter issueFilter = new DefaultIssueFilter().setId(1L).setName("My filter").setShared(true).setUser("new.owner");
      service.update(issueFilter, MockUserSession.create().setUserId(1).setLogin(currentUser));
      fail();
    } catch (Exception e) {
      assertThat(e).isInstanceOf(ForbiddenException.class).hasMessage("User is not authorized to change the owner of this filter");
    }

    verify(issueFilterDao, never()).update(any(IssueFilterDto.class));
  }

  @Test
  public void should_delete() {
    when(issueFilterDao.selectById(1L)).thenReturn(new IssueFilterDto().setId(1L).setName("My Issues").setUserLogin("john"));

    service.delete(1L, userSession);

    verify(issueFilterDao).delete(1L);
  }

  @Test
  public void should_delete_favorite_filter_on_delete() {
    when(issueFilterDao.selectById(1L)).thenReturn(new IssueFilterDto().setId(1L).setName("My Issues").setUserLogin("john"));
    when(issueFilterFavouriteDao.selectByFilterId(1L)).thenReturn(newArrayList(new IssueFilterFavouriteDto().setId(10L).setUserLogin("john").setIssueFilterId(1L)));

    service.delete(1L, userSession);

    verify(issueFilterDao).delete(1L);
    verify(issueFilterFavouriteDao).deleteByFilterId(1L);
  }

  @Test
  public void should_not_delete_if_filter_not_found() {
    when(issueFilterDao.selectById(1L)).thenReturn(null);

    try {
      service.delete(1L, userSession);
      fail();
    } catch (Exception e) {
      assertThat(e).isInstanceOf(NotFoundException.class).hasMessage("Filter not found: 1");
    }
    verify(issueFilterDao, never()).delete(anyLong());
  }

  @Test
  public void should_delete_shared_filter_if_user_is_admin() {
    when(authorizationDao.selectGlobalPermissions("john")).thenReturn(newArrayList(GlobalPermissions.SYSTEM_ADMIN));
    when(issueFilterDao.selectById(1L)).thenReturn(new IssueFilterDto().setId(1L).setName("My Issues").setUserLogin("arthur").setShared(true));

    service.delete(1L, userSession);

    verify(issueFilterDao).delete(1L);
  }

  @Test
  public void should_not_delete_not_shared_filter_if_user_is_admin() {
    when(authorizationDao.selectGlobalPermissions("john")).thenReturn(newArrayList(GlobalPermissions.SYSTEM_ADMIN));
    when(issueFilterDao.selectById(1L)).thenReturn(new IssueFilterDto().setId(1L).setName("My Issues").setUserLogin("arthur").setShared(false));

    try {
      service.delete(1L, userSession);
      fail();
    } catch (Exception e) {
      assertThat(e).isInstanceOf(ForbiddenException.class).hasMessage("User is not authorized to read this filter");
    }
    verify(issueFilterDao, never()).delete(anyLong());
  }

  @Test
  public void should_not_delete_shared_filter_if_not_admin() {
    when(authorizationDao.selectGlobalPermissions("john")).thenReturn(newArrayList(UserRole.USER));
    when(issueFilterDao.selectById(1L)).thenReturn(new IssueFilterDto().setId(1L).setName("My Issues").setUserLogin("arthur").setShared(true));

    try {
      service.delete(1L, userSession);
      fail();
    } catch (Exception e) {
      assertThat(e).isInstanceOf(ForbiddenException.class).hasMessage("User is not authorized to modify this filter");
    }
    verify(issueFilterDao, never()).delete(anyLong());
  }

  @Test
  public void should_copy() {
    when(issueFilterDao.selectById(1L)).thenReturn(new IssueFilterDto().setId(1L).setName("My Issues").setUserLogin("john").setData("componentRoots=struts"));
    DefaultIssueFilter issueFilter = new DefaultIssueFilter().setName("Copy Of My Issue");

    DefaultIssueFilter result = service.copy(1L, issueFilter, userSession);
    assertThat(result.name()).isEqualTo("Copy Of My Issue");
    assertThat(result.user()).isEqualTo("john");
    assertThat(result.data()).isEqualTo("componentRoots=struts");

    verify(issueFilterDao).insert(any(IssueFilterDto.class));
  }

  @Test
  public void should_copy_shared_filter() {
    when(issueFilterDao.selectById(1L)).thenReturn(new IssueFilterDto().setId(1L).setName("My Issues").setUserLogin("arthur").setShared(true));
    DefaultIssueFilter issueFilter = new DefaultIssueFilter().setName("Copy Of My Issue");

    DefaultIssueFilter result = service.copy(1L, issueFilter, userSession);
    assertThat(result.name()).isEqualTo("Copy Of My Issue");
    assertThat(result.user()).isEqualTo("john");
    assertThat(result.shared()).isFalse();

    verify(issueFilterDao).insert(any(IssueFilterDto.class));
  }

  @Test
  public void should_add_favorite_on_copy() {
    when(issueFilterDao.selectById(1L)).thenReturn(new IssueFilterDto().setId(1L).setName("My Issues").setUserLogin("john").setData("componentRoots=struts"));
    DefaultIssueFilter issueFilter = new DefaultIssueFilter().setName("Copy Of My Issue");
    service.copy(1L, issueFilter, userSession);

    verify(issueFilterDao).insert(any(IssueFilterDto.class));
    verify(issueFilterFavouriteDao).insert(any(IssueFilterFavouriteDto.class));
  }

  @Test
  public void should_execute_from_issue_query() {
    IssueQuery issueQuery = IssueQuery.builder().build();

    service.execute(issueQuery);

    verify(issueFinder).find(issueQuery);
  }

  @Test
  public void should_find_shared_issue_filter() {
    when(issueFilterDao.selectSharedFilters()).thenReturn(newArrayList(
        new IssueFilterDto().setId(1L).setName("My Issue").setUserLogin("john").setShared(true),
        new IssueFilterDto().setId(2L).setName("Project Issues").setUserLogin("arthur").setShared(true)
        ));

    List<DefaultIssueFilter> results = service.findSharedFiltersWithoutUserFilters(userSession);
    assertThat(results).hasSize(1);
    DefaultIssueFilter filter = results.get(0);
    assertThat(filter.name()).isEqualTo("Project Issues");
  }

  @Test
  public void should_find_favourite_issue_filter() {
    when(issueFilterDao.selectFavoriteFiltersByUser("john")).thenReturn(newArrayList(new IssueFilterDto().setId(1L).setName("My Issue").setUserLogin("john")));

    List<DefaultIssueFilter> results = service.findFavoriteFilters(userSession);
    assertThat(results).hasSize(1);
  }

  @Test
  public void should_not_find_favourite_issue_filter_if_not_logged() {
    UserSession userSession = MockUserSession.create().setLogin(null);

    try {
      service.findFavoriteFilters(userSession);
      fail();
    } catch (Exception e) {
      assertThat(e).isInstanceOf(UnauthorizedException.class).hasMessage("User is not logged in");
    }
  }

  @Test
  public void should_add_favourite_issue_filter_id() {
    when(issueFilterDao.selectById(1L)).thenReturn(new IssueFilterDto().setId(1L).setName("My Issues").setUserLogin("john").setData("componentRoots=struts"));
    // The filter is not in the favorite list --> add to favorite
    when(issueFilterFavouriteDao.selectByFilterId(1L)).thenReturn(Collections.<IssueFilterFavouriteDto> emptyList());

    ArgumentCaptor<IssueFilterFavouriteDto> issueFilterFavouriteDtoCaptor = ArgumentCaptor.forClass(IssueFilterFavouriteDto.class);
    boolean result = service.toggleFavouriteIssueFilter(1L, userSession);
    assertThat(result).isTrue();
    verify(issueFilterFavouriteDao).insert(issueFilterFavouriteDtoCaptor.capture());

    IssueFilterFavouriteDto issueFilterFavouriteDto = issueFilterFavouriteDtoCaptor.getValue();
    assertThat(issueFilterFavouriteDto.getIssueFilterId()).isEqualTo(1L);
    assertThat(issueFilterFavouriteDto.getUserLogin()).isEqualTo("john");
  }

  @Test
  public void should_add_favourite_on_shared_filter() {
    when(issueFilterDao.selectById(1L)).thenReturn(new IssueFilterDto().setId(1L).setName("My Issues").setUserLogin("arthur").setShared(true));
    // The filter is not in the favorite list --> add to favorite
    when(issueFilterFavouriteDao.selectByFilterId(1L)).thenReturn(Collections.<IssueFilterFavouriteDto> emptyList());

    ArgumentCaptor<IssueFilterFavouriteDto> issueFilterFavouriteDtoCaptor = ArgumentCaptor.forClass(IssueFilterFavouriteDto.class);
    boolean result = service.toggleFavouriteIssueFilter(1L, userSession);
    assertThat(result).isTrue();
    verify(issueFilterFavouriteDao).insert(issueFilterFavouriteDtoCaptor.capture());

    IssueFilterFavouriteDto issueFilterFavouriteDto = issueFilterFavouriteDtoCaptor.getValue();
    assertThat(issueFilterFavouriteDto.getIssueFilterId()).isEqualTo(1L);
    assertThat(issueFilterFavouriteDto.getUserLogin()).isEqualTo("john");
  }

  @Test
  public void should_delete_favourite_issue_filter_id() {
    when(issueFilterDao.selectById(1L)).thenReturn(new IssueFilterDto().setId(1L).setName("My Issues").setUserLogin("john").setData("componentRoots=struts"));
    // The filter is in the favorite list --> remove favorite
    when(issueFilterFavouriteDao.selectByFilterId(1L)).thenReturn(newArrayList(new IssueFilterFavouriteDto().setId(10L).setUserLogin("john").setIssueFilterId(1L)));

    boolean result = service.toggleFavouriteIssueFilter(1L, userSession);
    assertThat(result).isFalse();
    verify(issueFilterFavouriteDao).delete(10L);
  }

  @Test
  public void should_not_toggle_favourite_filter_if_filter_not_found() {
    when(issueFilterDao.selectById(1L)).thenReturn(null);
    try {
      service.toggleFavouriteIssueFilter(1L, userSession);
      fail();
    } catch (Exception e) {
      assertThat(e).isInstanceOf(NotFoundException.class).hasMessage("Filter not found: 1");
    }
    verify(issueFilterFavouriteDao, never()).delete(anyLong());
  }

  @Test
  public void should_serialize_filter_query_ignore_unknown_parameter() {
    Map<String, Object> props = newHashMap();
    props.put("componentRoots", "struts");
    props.put("statuses", "OPEN");
    props.put("unkwown", "JOHN");
    service.serializeFilterQuery(props);

    Map<String, Object> expected = newHashMap();
    expected.put("componentRoots", "struts");
    expected.put("statuses", "OPEN");
    verify(issueFilterSerializer).serialize(expected);
  }

  @Test
  public void should_deserialize_filter_query() {
    DefaultIssueFilter issueFilter = new DefaultIssueFilter().setData("componentRoots=struts");
    service.deserializeIssueFilterQuery(issueFilter);
    verify(issueFilterSerializer).deserialize("componentRoots=struts");
  }

  @Test
  public void user_can_share_filter_if_logged_and_own_sharing_permission(){
    when(authorizationDao.selectGlobalPermissions("john")).thenReturn(newArrayList(GlobalPermissions.DASHBOARD_SHARING));
    UserSession userSession = MockUserSession.create().setLogin("john");
    assertThat(service.canShareFilter(userSession)).isTrue();

    userSession = MockUserSession.create().setLogin(null);
    assertThat(service.canShareFilter(userSession)).isFalse();

    when(authorizationDao.selectGlobalPermissions("john")).thenReturn(Collections.<String>emptyList());
    userSession = MockUserSession.create().setLogin("john");
    assertThat(service.canShareFilter(userSession)).isFalse();
  }

  private static class Matches extends BaseMatcher<IssueFilterDto> {

    private final IssueFilterDto referenceFilter;

    private Matches(IssueFilterDto reference) {
      referenceFilter = reference;
    }

    static Matches filter(IssueFilterDto filterDto) {
      return new Matches(filterDto);
    }

    @Override
    public boolean matches(Object o) {
      if (o != null && o instanceof IssueFilterDto) {
        IssueFilterDto otherFilter = (IssueFilterDto) o;
        return ObjectUtils.equals(referenceFilter.isShared(), otherFilter.isShared())
          && ObjectUtils.equals(referenceFilter.getUserLogin(), otherFilter.getUserLogin())
          && ObjectUtils.equals(referenceFilter.getDescription(), otherFilter.getDescription())
          && ObjectUtils.equals(referenceFilter.getName(), otherFilter.getName())
          && ObjectUtils.equals(referenceFilter.getData(), otherFilter.getData());
      }
      return false;
    }

    @Override
    public void describeTo(Description description) {
    }
  }

}
